package com.interview.modules.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import com.interview.infrastructure.ai.StructuredOutputInvoker;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewSessionStatus;
import com.interview.modules.interview.model.InterviewSessionCacheRefreshEvent;
import com.interview.modules.interview.model.InterviewEvaluationRequestedEvent;
import com.interview.modules.interview.model.InterviewReportResponse;
import com.interview.modules.interview.model.InterviewReportStatusResponse;
import com.interview.modules.interview.model.InterviewEvidenceSnapshotDTO;
import com.interview.modules.interview.model.InterviewTopicScoreDTO;
import com.interview.modules.interview.model.InterviewTurnEvaluationDTO;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.repository.InterviewTopicRepository;
import com.interview.modules.interview.repository.InterviewTurnRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
@Service
public class InterviewEvaluationService {
  private final InterviewSessionRepository sessionRepository;
  private final InterviewTopicRepository topicRepository;
  private final InterviewTurnRepository turnRepository;
  private final LlmProviderRegistry llmRegistry;
  private final StructuredOutputInvoker invoker;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final InterviewEvaluationProperties properties;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;

  public InterviewEvaluationService(InterviewSessionRepository sessionRepository,
      InterviewTopicRepository topicRepository, InterviewTurnRepository turnRepository,
      LlmProviderRegistry llmRegistry, StructuredOutputInvoker invoker,
      ObjectMapper objectMapper, TransactionTemplate transactionTemplate,
      ApplicationEventPublisher eventPublisher, InterviewEvaluationProperties properties,
      ResourceLoader resourceLoader) throws IOException {
    this.sessionRepository = sessionRepository;
    this.topicRepository = topicRepository;
    this.turnRepository = turnRepository;
    this.llmRegistry = llmRegistry;
    this.invoker = invoker;
    this.objectMapper = objectMapper;
    this.transactionTemplate = transactionTemplate;
    this.eventPublisher = eventPublisher;
    this.properties = properties;
    this.systemPromptTemplate = new PromptTemplate(resourceLoader
        .getResource(properties.getSystemPromptPath())
        .getContentAsString(StandardCharsets.UTF_8));
    this.userPromptTemplate = new PromptTemplate(resourceLoader
        .getResource(properties.getUserPromptPath())
        .getContentAsString(StandardCharsets.UTF_8));
  }

  @Transactional
  public boolean markProcessing(String sessionId) {
    InterviewSessionEntity session = find(sessionId);
    if (session.getEvaluateStatus() == null) session.markEvaluationPending();
    if (session.getEvaluateStatus() == AsyncTaskStatus.COMPLETED) return false;
    session.markEvaluationProcessing();
    sessionRepository.save(session);
    return true;
  }

  public boolean evaluate(String sessionId) {
    InterviewSessionEntity current = find(sessionId);
    if (current.getStatus() != InterviewSessionStatus.COMPLETED
        || current.getEvaluateStatus() != AsyncTaskStatus.PROCESSING) {
      log.info("Skipping interview evaluation: sessionId={}, status={}, evaluateStatus={}",
          sessionId, current.getStatus(), current.getEvaluateStatus());
      return false;
    }

    EvaluationInput fullInput = loadInput(sessionId);
    BeanOutputConverter<EvaluationOutput> converter =
        new BeanOutputConverter<>(EvaluationOutput.class);
    String systemPrompt = systemPromptTemplate.render(Map.of("format", converter.getFormat()));
    EvaluationInput input = limitEvidence(fullInput, systemPrompt);
    String userPrompt = renderUserPrompt(input);
    log.info("Interview evaluation context prepared: sessionId={}, promptChars={}, evidenceCount={}",
        sessionId, systemPrompt.length() + userPrompt.length(),
        input.topics().stream().mapToInt(topic -> topic.evidence().size()).sum());
    EvaluationOutput output = invoker.invoke(llmRegistry.getDefaultChatClient(),
        systemPrompt, userPrompt, converter);
    ValidatedReport report = validateAndMap(input, output);
    saveCompleted(sessionId, report);
    return true;
  }

  @Transactional(readOnly = true)
  public InterviewReportStatusResponse getReport(String sessionId) {
    InterviewSessionEntity session = find(sessionId);
    AsyncTaskStatus status = session.getEvaluateStatus() == null
        ? AsyncTaskStatus.PENDING : session.getEvaluateStatus();
    InterviewReportResponse report = null;
    if (status == AsyncTaskStatus.COMPLETED) {
      if (session.getEvaluationReport() == null || session.getEvaluationReport().isBlank()) {
        throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
            "Evaluation status is completed but report is missing");
      }
      ReportContent content = fromJson(session.getEvaluationReport());
      report = new InterviewReportResponse(session.getSessionId(),
          content.overallScore(), content.overallFeedback(), content.strengths(),
          content.improvements(), content.topicScores(), content.turnEvaluations(),
          session.getEvaluatedAt());
    }
    return new InterviewReportStatusResponse(status, session.getEvaluateError(), report);
  }

  @Transactional
  public InterviewReportStatusResponse retry(String sessionId) {
    InterviewSessionEntity session = find(sessionId);
    if (session.getStatus() != InterviewSessionStatus.COMPLETED
        || session.getEvaluateStatus() != AsyncTaskStatus.FAILED) {
      throw new BusinessException(ErrorCode.BAD_REQUEST,
          "Only failed interview evaluations can be retried");
    }
    session.markEvaluationPending();
    sessionRepository.save(session);
    eventPublisher.publishEvent(new InterviewEvaluationRequestedEvent(sessionId));
    eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(sessionId));
    return new InterviewReportStatusResponse(AsyncTaskStatus.PENDING, null, null);
  }

  @Transactional
  public void markFailed(String sessionId, String error) {
    InterviewSessionEntity session = sessionRepository.findBySessionId(sessionId).orElse(null);
    if (session == null || session.getEvaluateStatus() == AsyncTaskStatus.COMPLETED) return;
    session.markEvaluationFailed(error);
    sessionRepository.save(session);
  }

  @Transactional(readOnly = true)
  protected EvaluationInput loadInput(String sessionId) {
    InterviewSessionEntity session = find(sessionId);
    List<TopicInput> topics = topicRepository
        .findBySession_SessionIdOrderByTopicOrderAsc(sessionId).stream()
        .map(topic -> new TopicInput(topic.getId(), topic.getTopicOrder(), topic.getCategory(),
            topic.getTopicName(), topic.getObjective(), topic.getScoringPointsJson(),
            topic.getReferenceAnswer(),
            parseEvidenceSnapshot(topic.getEvidenceSnapshotJson()).stream()
                .map(InterviewEvaluationService::toEvidenceInput).toList()))
        .toList();
    List<TurnInput> turns = turnRepository.findBySession_SessionIdOrderByTurnOrderAsc(sessionId)
        .stream().map(turn -> new TurnInput(turn.getId(), turn.getTurnOrder(),
            turn.getTopic().getId(), turn.getTurnType(),
            turn.getParentTurn() == null ? null : turn.getParentTurn().getId(),
            turn.getQuestion(), turn.getAnswer(), turn.getDecision())).toList();
    return new EvaluationInput(session.getSessionId(), topics, turns);
  }

  private InterviewSessionEntity find(String sessionId) {
    return sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
  }

  private String renderUserPrompt(EvaluationInput input) {
    try {
      return userPromptTemplate.render(Map.of(
          "interviewSnapshot", objectMapper.writeValueAsString(input)));
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
          "Failed to build interview evaluation prompt", ex);
    }
  }

  private EvaluationInput limitEvidence(EvaluationInput input, String systemPrompt) {
    Map<Long, List<EvidenceInput>> candidates = new LinkedHashMap<>();
    Map<Long, List<EvidenceInput>> selected = new LinkedHashMap<>();
    for (TopicInput topic : input.topics()) {
      List<EvidenceInput> sorted = safe(topic.evidence()).stream()
          .sorted(Comparator.comparing(EvidenceInput::score,
              Comparator.nullsLast(Comparator.reverseOrder())))
          .limit(Math.max(0, properties.getMaxEvidencePerTopic()))
          .map(this::truncateEvidence)
          .toList();
      candidates.put(topic.topicId(), sorted);
      selected.put(topic.topicId(), new ArrayList<>());
    }
    EvaluationInput limited = withEvidence(input, selected);
    ensureWithinBudget(systemPrompt, limited);
    Set<Long> exhausted = new java.util.HashSet<>();
    for (int rank = 0; rank < Math.max(0, properties.getMaxEvidencePerTopic()); rank++) {
      for (TopicInput topic : input.topics()) {
        List<EvidenceInput> topicCandidates = candidates.get(topic.topicId());
        if (exhausted.contains(topic.topicId()) || rank >= topicCandidates.size()) continue;
        List<EvidenceInput> topicSelected = selected.get(topic.topicId());
        topicSelected.add(topicCandidates.get(rank));
        EvaluationInput tentative = withEvidence(input, selected);
        if (promptLength(systemPrompt, tentative) <= properties.getMaxPromptChars()) {
          limited = tentative;
        } else {
          topicSelected.remove(topicSelected.size() - 1);
          exhausted.add(topic.topicId());
        }
      }
    }
    return limited;
  }

  private EvaluationInput withEvidence(EvaluationInput input,
      Map<Long, List<EvidenceInput>> selected) {
    List<TopicInput> topics = input.topics().stream().map(topic -> new TopicInput(
        topic.topicId(), topic.topicOrder(), topic.category(), topic.topicName(),
        topic.objective(), topic.scoringPoints(), topic.referenceAnswer(),
        List.copyOf(selected.getOrDefault(topic.topicId(), List.of())))).toList();
    return new EvaluationInput(input.sessionId(), topics, input.turns());
  }

  private EvidenceInput truncateEvidence(EvidenceInput evidence) {
    String content = evidence.content();
    int maxChars = Math.max(0, properties.getMaxEvidenceChars());
    if (content != null && content.length() > maxChars) content = content.substring(0, maxChars);
    return new EvidenceInput(evidence.knowledgeBaseId(), evidence.chunkId(), evidence.sourceName(),
        evidence.sourceKey(), evidence.sourceType(), evidence.category(), evidence.version(),
        evidence.score(), content);
  }

  private void ensureWithinBudget(String systemPrompt, EvaluationInput input) {
    int length = promptLength(systemPrompt, input);
    if (length > properties.getMaxPromptChars()) {
      throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
          "Interview context exceeds prompt budget before evidence is added: " + length);
    }
  }

  private int promptLength(String systemPrompt, EvaluationInput input) {
    return systemPrompt.length() + renderUserPrompt(input).length();
  }

  private List<InterviewEvidenceSnapshotDTO> parseEvidenceSnapshot(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
          "Failed to read interview evidence snapshot", ex);
    }
  }

  private void saveCompleted(String sessionId, ValidatedReport report) {
    String reportJson = toJson(new ReportContent(report.overallScore(),
        report.overallFeedback(), report.strengths(), report.improvements(),
        report.topicScores(), report.turnEvaluations()));
    transactionTemplate.executeWithoutResult(status -> {
      InterviewSessionEntity session = find(sessionId);
      session.completeEvaluation(report.overallScore(), reportJson, LocalDateTime.now());
      sessionRepository.save(session);
      eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(sessionId));
    });
  }

  private ReportContent fromJson(String value) {
    try {
      return objectMapper.readValue(value, ReportContent.class);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
          "Failed to read interview evaluation report", ex);
    }
  }

  private String toJson(ReportContent report) {
    try {
      return objectMapper.writeValueAsString(report);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
          "Failed to save interview evaluation report", ex);
    }
  }

  private ValidatedReport validateAndMap(EvaluationInput input, EvaluationOutput output) {
    if (output == null || output.overallScore() == null || output.overallFeedback() == null) {
      throw invalidOutput("Evaluation output is incomplete");
    }
    validateScore(output.overallScore(), "overallScore");
    Map<Long, TopicInput> topics = input.topics().stream()
        .collect(Collectors.toMap(TopicInput::topicId, Function.identity()));
    Map<Long, TurnInput> answeredTurns = input.turns().stream()
        .filter(turn -> turn.answer() != null && !turn.answer().isBlank())
        .collect(Collectors.toMap(TurnInput::turnId, Function.identity()));
    List<TopicScoreOutput> topicOutputs = safe(output.topicScores());
    List<TurnEvaluationOutput> turnOutputs = safe(output.turnEvaluations());
    requireExactIds(topics.keySet(), topicOutputs.stream().map(TopicScoreOutput::topicId).toList(),
        "topicScores");
    requireExactIds(answeredTurns.keySet(),
        turnOutputs.stream().map(TurnEvaluationOutput::turnId).toList(), "turnEvaluations");

    List<InterviewTopicScoreDTO> topicScores = topicOutputs.stream().map(score -> {
      validateScore(score.score(), "topic score");
      TopicInput topic = topics.get(score.topicId());
      return new InterviewTopicScoreDTO(topic.topicId(), topic.topicOrder(), topic.topicName(),
          score.score(), score.feedback(), safe(score.coveredPoints()),
          safe(score.missingPoints()));
    }).toList();
    List<InterviewTurnEvaluationDTO> turnEvaluations = turnOutputs.stream().map(evaluation -> {
      validateScore(evaluation.score(), "turn score");
      TurnInput turn = answeredTurns.get(evaluation.turnId());
      return new InterviewTurnEvaluationDTO(turn.turnId(), turn.turnOrder(), turn.topicId(),
          turn.question(), turn.answer(), turn.turnType(), turn.parentTurnId(), evaluation.score(),
          safe(evaluation.coveredPoints()),
          safe(evaluation.missingPoints()), evaluation.feedback());
    }).toList();
    return new ValidatedReport(output.overallScore(), output.overallFeedback(),
        safe(output.strengths()), safe(output.improvements()), topicScores, turnEvaluations);
  }

  private static void requireExactIds(Set<Long> expected, List<Long> actual, String field) {
    if (actual.stream().anyMatch(id -> id == null) || actual.size() != Set.copyOf(actual).size()
        || !expected.equals(Set.copyOf(actual))) {
      throw invalidOutput(field + " does not match the interview snapshot");
    }
  }

  private static void validateScore(Integer score, String field) {
    if (score == null || score < 0 || score > 100) {
      throw invalidOutput(field + " must be between 0 and 100");
    }
  }

  private static BusinessException invalidOutput(String message) {
    return new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED, message);
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  private static EvidenceInput toEvidenceInput(InterviewEvidenceSnapshotDTO evidence) {
    return new EvidenceInput(evidence.knowledgeBaseId(), evidence.chunkId(),
        evidence.sourceName(), evidence.sourceKey(), evidence.sourceType(),
        evidence.category(), evidence.version(), evidence.score(), evidence.content());
  }

  public record EvaluationInput(String sessionId, List<TopicInput> topics, List<TurnInput> turns) {}
  public record TopicInput(Long topicId, Integer topicOrder, String category, String topicName,
      String objective, String scoringPoints, String referenceAnswer,
      List<EvidenceInput> evidence) {}
  public record TurnInput(Long turnId, Integer turnOrder, Long topicId, String turnType,
      Long parentTurnId, String question, String answer, String decision) {}
  public record EvidenceInput(Long knowledgeBaseId, String chunkId, String sourceName,
      String sourceKey, String sourceType, String category, Integer version, Double score,
      String content) {}
  public record EvaluationOutput(Integer overallScore, String overallFeedback,
      List<String> strengths, List<String> improvements, List<TopicScoreOutput> topicScores,
      List<TurnEvaluationOutput> turnEvaluations) {}
  public record TopicScoreOutput(Long topicId, Integer score, String feedback,
      List<String> coveredPoints, List<String> missingPoints) {}
  public record TurnEvaluationOutput(Long turnId, Integer score, List<String> coveredPoints,
      List<String> missingPoints, String feedback) {}
  private record ValidatedReport(Integer overallScore, String overallFeedback,
      List<String> strengths, List<String> improvements,
      List<InterviewTopicScoreDTO> topicScores,
      List<InterviewTurnEvaluationDTO> turnEvaluations) {}
  private record ReportContent(Integer overallScore, String overallFeedback,
      List<String> strengths, List<String> improvements,
      List<InterviewTopicScoreDTO> topicScores,
      List<InterviewTurnEvaluationDTO> turnEvaluations) {}
}
