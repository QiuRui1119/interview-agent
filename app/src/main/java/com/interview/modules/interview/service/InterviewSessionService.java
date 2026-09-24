package com.interview.modules.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.modules.interview.listener.InterviewGenerationStreamProducer;
import com.interview.modules.interview.model.CreateInterviewSessionRequest;
import com.interview.modules.interview.model.CreateInterviewSessionResponse;
import com.interview.modules.interview.model.InterviewRecentTurnCache;
import com.interview.modules.interview.model.InterviewResumeResponse;
import com.interview.modules.interview.model.InterviewSessionCache;
import com.interview.modules.interview.model.InterviewSessionCacheRefreshEvent;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewSessionListItemDTO;
import com.interview.modules.interview.model.InterviewTopicStatus;
import com.interview.modules.interview.model.InterviewTurnCache;
import com.interview.modules.interview.model.InterviewTurnEntity;
import com.interview.modules.interview.model.InterviewTurnStatus;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.repository.InterviewTopicRepository;
import com.interview.modules.interview.repository.InterviewTurnRepository;
import com.interview.modules.knowledgebase.model.VectorStatus;
import com.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.interview.modules.resume.repository.ResumeRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class InterviewSessionService {
  private final ResumeRepository resumeRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final InterviewSessionRepository sessionRepository;
  private final InterviewTopicRepository topicRepository;
  private final InterviewTurnRepository turnRepository;
  private final InterviewSessionCacheService cacheService;
  private final InterviewAnswerService answerService;
  private final InterviewGenerationStreamProducer generationProducer;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  public CreateInterviewSessionResponse create(CreateInterviewSessionRequest request) {
    validateCreateRequest(request);
    var resume = resumeRepository.findById(request.resumeId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
    if (resume.getAnalyzeStatus() != AsyncTaskStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Resume analysis is not completed");
    }
    Set<Long> kbIds = new LinkedHashSet<>(request.knowledgeBaseIds() == null
        ? List.of() : request.knowledgeBaseIds());
    if (kbIds.isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "At least one knowledge base is required");
    }
    var knowledgeBases = knowledgeBaseRepository.findAllById(kbIds);
    if (knowledgeBases.size() != kbIds.size() || knowledgeBases.stream().anyMatch(kb ->
        kb.getVectorStatus() != VectorStatus.COMPLETED || !Boolean.TRUE.equals(kb.getEnabled()))) {
      throw new BusinessException(ErrorCode.BAD_REQUEST,
          "All selected knowledge bases must exist, be enabled and completed");
    }
    InterviewSessionEntity session = transactionTemplate.execute(status -> {
      InterviewSessionEntity entity = InterviewSessionEntity.create();
      entity.configure(resume.getId(), value(request.skillId(), "java-backend"),
          value(request.difficulty(), "mid"), 5, 1, 10, kbIds);
      InterviewSessionEntity saved = sessionRepository.saveAndFlush(entity);
      eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(saved.getSessionId()));
      return saved;
    });
    generationProducer.publish(session.getSessionId());
    return new CreateInterviewSessionResponse(session.getSessionId(), session.getStatus());
  }

  public InterviewResumeResponse resume(String sessionId) {
    InterviewSessionCache cache = get(sessionId);
    InterviewTurnCache current = cache.currentTurn();
    if (shouldResumePending(current)) {
      String requestId = answerService.findRequestId(current.id());
      answerService.recoverDecision(sessionId, current.id(), requestId);
      cache = rebuild(sessionId);
    } else if (shouldResumeDeciding(sessionId, current)) {
      answerService.recoverDecision(sessionId, current.id(),
          answerService.findRequestId(current.id()));
      cache = rebuild(sessionId);
    }
    return toResponse(cache);
  }

  public List<InterviewSessionListItemDTO> findAll() {
    List<InterviewSessionEntity> sessions = sessionRepository.findAllByOrderByCreatedAtDesc();
    Map<Long, com.interview.modules.resume.model.ResumeEntity> resumes = resumeRepository
        .findAllById(sessions.stream().map(InterviewSessionEntity::getResumeId)
            .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(
            com.interview.modules.resume.model.ResumeEntity::getId, Function.identity()));
    return sessions.stream().map(session -> {
      var resume = resumes.get(session.getResumeId());
      return new InterviewSessionListItemDTO(session.getSessionId(), session.getResumeId(),
          resume == null ? null : resume.getOriginalFileName(), session.getSkillId(),
          session.getDifficulty(), session.getStatus(), session.getGenerationStatus(),
          session.getEvaluateStatus(), session.getOverallScore(), session.getCreatedAt(),
          session.getCompletedAt());
    }).toList();
  }

  public InterviewSessionCache get(String sessionId) {
    return get(sessionId, null);
  }

  public InterviewSessionCache get(String sessionId, Long minimumVersion) {
    validateSessionId(sessionId);
    InterviewSessionCache cached = cacheService.get(sessionId);
    return cached != null && cached.version() != null
        && (minimumVersion == null || cached.version() >= minimumVersion)
        ? cached : rebuild(sessionId);
  }

  public InterviewSessionCache rebuild(String sessionId) {
    validateSessionId(sessionId);
    InterviewSessionCache rebuilt = transactionTemplate.execute(status -> buildCache(sessionId));
    cacheService.put(rebuilt);
    return rebuilt;
  }

  private InterviewSessionCache buildCache(String sessionId) {
    InterviewSessionEntity session = sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    List<InterviewTurnEntity> recentTurns = turnRepository
        .findTop6BySession_SessionIdOrderByTurnOrderDesc(sessionId);
    InterviewTurnEntity currentTurn = resolveCurrentTurn(session, recentTurns);
    return new InterviewSessionCache(session.getSessionId(), session.getCurrentTurnId(),
        session.getCurrentTopicOrder(), session.getTotalTurns(), session.getStatus(),
        session.getGenerationStatus(), session.getEvaluateStatus(), session.getEvaluateError(),
        session.getVersion(), Instant.now(),
        Math.toIntExact(topicRepository.countBySession_SessionIdAndStatus(
            sessionId, InterviewTopicStatus.COMPLETED)),
        Math.toIntExact(turnRepository.countBySession_SessionIdAndStatus(
            sessionId, InterviewTurnStatus.DECIDED)),
        toCache(currentTurn), recentTurns.stream()
            .sorted(Comparator.comparing(InterviewTurnEntity::getTurnOrder))
            .map(InterviewSessionService::toRecentCache).toList());
  }

  private InterviewTurnEntity resolveCurrentTurn(InterviewSessionEntity session,
      List<InterviewTurnEntity> recentTurns) {
    if (session.getCurrentTurnId() == null) return null;
    return recentTurns.stream().filter(turn -> session.getCurrentTurnId().equals(turn.getId()))
        .findFirst().orElseGet(() -> turnRepository.findById(session.getCurrentTurnId()).orElse(null));
  }

  private boolean shouldResumePending(InterviewTurnCache turn) {
    return turn != null && turn.status() == InterviewTurnStatus.ANSWER_PENDING
        && turn.answer() != null && !turn.answer().isBlank();
  }

  private boolean shouldResumeDeciding(String sessionId, InterviewTurnCache turn) {
    return turn != null && turn.status() == InterviewTurnStatus.DECIDING
        && !answerService.hasTurnAfter(sessionId, turn.turnOrder());
  }

  private InterviewResumeResponse toResponse(InterviewSessionCache cache) {
    List<InterviewRecentTurnCache> recent = cache.recentTurns().stream()
        .map(turn -> new InterviewRecentTurnCache(turn.id(), turn.question(), turn.answer(),
            action(turn.decision()))).toList();
    return new InterviewResumeResponse(cache.sessionId(), cache.status(),
        cache.currentTopicOrder(), cache.completedTopicCount(), cache.totalTurns(),
        cache.generationStatus(), cache.evaluateStatus(), cache.evaluateError(),
        cache.currentTurn(), recent);
  }

  private String action(String decision) {
    if (decision == null || decision.isBlank()) return null;
    try {
      return objectMapper.readTree(decision).path("action").asText(null);
    } catch (Exception ex) {
      return null;
    }
  }

  private static InterviewTurnCache toCache(InterviewTurnEntity turn) {
    return turn == null ? null : new InterviewTurnCache(turn.getId(), turn.getTurnOrder(),
        turn.getQuestion(), turn.getAnswer(), turn.getTurnType(),
        turn.getParentTurn() == null ? null : turn.getParentTurn().getId(),
        turn.getDecision(), turn.getStatus());
  }

  private static InterviewRecentTurnCache toRecentCache(InterviewTurnEntity turn) {
    return new InterviewRecentTurnCache(turn.getId(), turn.getQuestion(), turn.getAnswer(),
        turn.getDecision());
  }

  private static void validateCreateRequest(CreateInterviewSessionRequest request) {
    if (request == null || request.resumeId() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "resumeId is required");
    }
  }

  private static void validateSessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Interview session id must not be blank");
    }
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.strip();
  }
}
