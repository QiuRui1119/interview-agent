package com.interview.modules.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import com.interview.infrastructure.ai.StructuredOutputInvoker;
import com.interview.modules.interview.model.InterviewEvidenceSnapshotDTO;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewSessionStatus;
import com.interview.modules.interview.model.InterviewTopicEntity;
import com.interview.modules.interview.model.InterviewTopicStatus;
import com.interview.modules.interview.model.InterviewTurnEntity;
import com.interview.modules.interview.model.InterviewTurnStatus;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.repository.InterviewTopicRepository;
import com.interview.modules.interview.repository.InterviewTurnRepository;
import com.interview.modules.knowledgebase.model.RetrievalRequest;
import com.interview.modules.knowledgebase.model.RetrievedChunkDTO;
import com.interview.modules.knowledgebase.service.KnowledgeRetrievalService;
import com.interview.modules.resume.model.ResumeAnalysisEntity;
import com.interview.modules.resume.model.ResumeEntity;
import com.interview.modules.resume.repository.ResumeAnalysisRepository;
import com.interview.modules.resume.repository.ResumeRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InterviewGenerationService {
  private final InterviewSessionRepository sessionRepository;
  private final InterviewTopicRepository topicRepository;
  private final InterviewTurnRepository turnRepository;
  private final ResumeRepository resumeRepository;
  private final ResumeAnalysisRepository analysisRepository;
  private final KnowledgeRetrievalService retrievalService;
  private final LlmProviderRegistry llmRegistry;
  private final StructuredOutputInvoker invoker;
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;
  private final TransactionTemplate transactionTemplate;

  public InterviewGenerationService(InterviewSessionRepository sessionRepository,
      InterviewTopicRepository topicRepository, InterviewTurnRepository turnRepository,
      ResumeRepository resumeRepository, ResumeAnalysisRepository analysisRepository,
      KnowledgeRetrievalService retrievalService, LlmProviderRegistry llmRegistry,
      StructuredOutputInvoker invoker, ObjectMapper objectMapper, ResourceLoader resourceLoader,
      TransactionTemplate transactionTemplate) {
    this.sessionRepository = sessionRepository;
    this.topicRepository = topicRepository;
    this.turnRepository = turnRepository;
    this.resumeRepository = resumeRepository;
    this.analysisRepository = analysisRepository;
    this.retrievalService = retrievalService;
    this.llmRegistry = llmRegistry;
    this.invoker = invoker;
    this.objectMapper = objectMapper;
    this.resourceLoader = resourceLoader;
    this.transactionTemplate = transactionTemplate;
  }

  public boolean generate(String sessionId) {
    InterviewSessionEntity session = find(sessionId);
    if (session.getGenerationStatus() == AsyncTaskStatus.COMPLETED) return false;
    if (session.getStatus() != InterviewSessionStatus.CREATED) {
      throw new BusinessException(ErrorCode.INVALID_INTERVIEW_STATE_TRANSITION,
          "Session cannot generate topics in state: " + session.getStatus());
    }
    ResumeEntity resume = resumeRepository.findById(session.getResumeId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
    ResumeAnalysisEntity analysis = analysisRepository
        .findByResumeEntityOrderByAnalysisTimeDesc(resume).stream().findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_ANALYSIS_NOT_FOUND));
    List<TopicPlan> plans = planTopics(session, resume, analysis);
    List<GeneratedTopic> generated = plans.stream().limit(5)
        .map(plan -> generateTopic(session, plan)).toList();
    if (generated.size() != 5) {
      throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
          "Topic planner must return exactly 5 topics");
    }
    save(sessionId, generated);
    return true;
  }

  public void markFailed(String sessionId, String error) {
    transactionTemplate.executeWithoutResult(status -> sessionRepository.findBySessionId(sessionId)
        .filter(session -> session.getStatus() == InterviewSessionStatus.CREATED)
        .ifPresent(session -> {
          session.markGenerationFailed(error);
          sessionRepository.save(session);
        }));
  }

  public void markProcessing(String sessionId) {
    transactionTemplate.executeWithoutResult(status -> {
      InterviewSessionEntity session = find(sessionId);
      session.markGenerationProcessing();
      sessionRepository.save(session);
    });
  }

  private void save(String sessionId, List<GeneratedTopic> generated) {
    transactionTemplate.executeWithoutResult(status -> {
      InterviewSessionEntity session = find(sessionId);
      if (session.getStatus() != InterviewSessionStatus.CREATED
          || session.getGenerationStatus() != AsyncTaskStatus.PROCESSING) return;
      InterviewTurnEntity firstTurn = null;
      for (int index = 0; index < generated.size(); index++) {
        GeneratedTopic data = generated.get(index);
        InterviewTopicEntity topic = InterviewTopicEntity.create(
            session, data.category(), data.topicName(), index);
        topic.applyGeneratedContent(data.objective(), data.retrievalQuery(), data.baseQuestion(),
            data.fallbackFollowUp(), data.referenceAnswer(), data.scoringPointsJson(),
            toEvidenceJson(data.evidence()), 1);
        if (index == 0) topic.transitionTo(InterviewTopicStatus.ACTIVE);
        topic = topicRepository.save(topic);
        if (index == 0) {
          firstTurn = InterviewTurnEntity.create(topic, 0, data.baseQuestion());
          firstTurn.transitionTo(InterviewTurnStatus.ANSWER_PENDING);
          firstTurn = turnRepository.saveAndFlush(firstTurn);
        }
      }
      if (firstTurn == null) {
        throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
            "No interview question was generated");
      }
      session.markGenerationCompleted(firstTurn.getId());
      sessionRepository.save(session);
    });
  }

  private List<TopicPlan> planTopics(InterviewSessionEntity session, ResumeEntity resume,
      ResumeAnalysisEntity analysis) {
    BeanOutputConverter<TopicPlanResponse> converter =
        new BeanOutputConverter<>(TopicPlanResponse.class);
    String prompt = "Generate exactly 5 interview topics as JSON. skill=" + session.getSkillId()
        + ", difficulty=" + session.getDifficulty() + "\nResume:\n" + resume.getResumeText()
        + "\nAnalysis:\n" + analysis.getSummary() + "\nSkill instructions:\n"
        + loadSkill(session.getSkillId());
    return invoker.invoke(llmRegistry.getDefaultChatClient(),
        "You plan focused technical interviews. Return diverse topics.\n" + converter.getFormat(),
        prompt, converter).topics();
  }

  private GeneratedTopic generateTopic(InterviewSessionEntity session, TopicPlan plan) {
    List<RetrievedChunkDTO> evidence = retrievalService.retrieve(new RetrievalRequest(
        plan.retrievalQuery(), List.copyOf(session.getKnowledgeBaseIds()), 4, 0.3));
    BeanOutputConverter<QuestionOutput> converter = new BeanOutputConverter<>(QuestionOutput.class);
    String context = evidence.stream().map(RetrievedChunkDTO::content)
        .reduce("", (left, right) -> left + "\n---\n" + right);
    QuestionOutput output = invoker.invoke(llmRegistry.getDefaultChatClient(),
        "Generate one main question, one fallback follow-up, a reference answer and scoring points. "
            + "Use only the supplied evidence.\n" + converter.getFormat(),
        "Topic: " + plan.topicName() + "\nObjective: " + plan.objective()
            + "\nEvidence:\n" + context, converter);
    return new GeneratedTopic(plan.category(), plan.topicName(), plan.objective(),
        plan.retrievalQuery(), output.baseQuestion(), output.fallbackFollowUp(),
        output.referenceAnswer(), json(output.scoringPoints()), evidence);
  }

  private InterviewSessionEntity find(String sessionId) {
    return sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
  }

  private String toEvidenceJson(List<RetrievedChunkDTO> evidence) {
    List<InterviewEvidenceSnapshotDTO> snapshots = evidence == null ? List.of()
        : evidence.stream().map(chunk -> new InterviewEvidenceSnapshotDTO(
            chunk.knowledgeBaseId(), chunk.chunkId(), chunk.sourceName(), chunk.sourceKey(),
            chunk.sourceType(), chunk.category(), chunk.version(), chunk.score(),
            chunk.content())).toList();
    try {
      return objectMapper.writeValueAsString(snapshots);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
          "Failed to serialize interview evidence snapshot", ex);
    }
  }

  private String json(List<String> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? List.of() : value);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
          "Failed to serialize interview scoring points", ex);
    }
  }

  private String loadSkill(String skillId) {
    String normalized = skillId == null || skillId.isBlank() ? "java-backend" : skillId;
    try {
      return resourceLoader.getResource("classpath:skills/" + normalized + "/SKILL.md")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
          "Skill definition not found: " + normalized, ex);
    }
  }

  private record TopicPlanResponse(List<TopicPlan> topics) {}
  private record TopicPlan(String category, String topicName, String objective,
      String retrievalQuery) {}
  private record QuestionOutput(String baseQuestion, String fallbackFollowUp,
      String referenceAnswer, List<String> scoringPoints) {}
  public record GeneratedTopic(String category, String topicName, String objective,
      String retrievalQuery, String baseQuestion, String fallbackFollowUp,
      String referenceAnswer, String scoringPointsJson, List<RetrievedChunkDTO> evidence) {}
}
