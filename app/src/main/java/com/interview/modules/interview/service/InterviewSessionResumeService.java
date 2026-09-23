package com.interview.modules.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.modules.interview.model.InterviewRecentTurnCache;
import com.interview.modules.interview.model.InterviewResumeResponse;
import com.interview.modules.interview.model.InterviewSessionCache;
import com.interview.modules.interview.model.InterviewTurnCache;
import com.interview.modules.interview.model.InterviewTurnStatus;
import com.interview.modules.interview.repository.InterviewTurnRepository;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewSessionResumeService {
  private final InterviewSessionReadService readService;
  private final InterviewAnswerPersistenceService answerPersistenceService;
  private final InterviewAnswerDecisionService decisionService;
  private final InterviewTurnRepository turnRepository;
  private final ObjectMapper objectMapper;

  public InterviewResumeResponse resume(String sessionId) {
    InterviewSessionCache cache = readService.get(sessionId);
    InterviewTurnCache current = cache.currentTurn();
    if (shouldResumePending(current)) {
      answerPersistenceService.markDeciding(sessionId, current.id(),
          findRequestId(current.id()));
      recoverDecision(sessionId, current.id(), findRequestId(current.id()));
      cache = readService.rebuild(sessionId);
    } else if (shouldResumeDeciding(sessionId, current)) {
      recoverDecision(sessionId, current.id(), findRequestId(current.id()));
      cache = readService.rebuild(sessionId);
    }
    return toResponse(cache);
  }

  private void recoverDecision(String sessionId, Long turnId, String requestId) {
    try {
      var decision = decisionService.decide(sessionId, turnId);
      decisionService.persist(sessionId, turnId, requestId, decision);
    } catch (BusinessException ex) {
      if (ex.getCode().equals(ErrorCode.AI_SERVICE_ERROR.getCode())
          || ex.getCode().equals(ErrorCode.AI_SERVICE_TIMEOUT.getCode())) {
        var fallback = decisionService.fallbackDecision(sessionId, turnId);
        if (fallback != null) {
          decisionService.persist(sessionId, turnId, requestId, fallback);
        }
        return;
      }
      throw ex;
    }
  }

  private boolean shouldResumePending(InterviewTurnCache turn) {
    return turn != null && turn.status() == InterviewTurnStatus.ANSWER_PENDING
        && turn.answer() != null && !turn.answer().isBlank();
  }

  private boolean shouldResumeDeciding(String sessionId, InterviewTurnCache turn) {
    return turn != null && turn.status() == InterviewTurnStatus.DECIDING
        && !turnRepository.existsBySession_SessionIdAndTurnOrderGreaterThan(
            sessionId, turn.turnOrder());
  }

  private String findRequestId(Long turnId) {
    return turnRepository.findById(turnId).map(turn -> turn.getRequestId()).orElse(null);
  }

  private InterviewResumeResponse toResponse(InterviewSessionCache cache) {
    List<InterviewRecentTurnCache> recent = cache.recentTurns().stream()
        .map(turn -> new InterviewRecentTurnCache(
            turn.id(), turn.question(), turn.answer(), action(turn.decision())))
        .toList();
    return new InterviewResumeResponse(cache.sessionId(), cache.status(),
        cache.currentTopicOrder(), cache.completedTopicCount(), cache.totalTurns(),
        cache.generationStatus(),
        cache.currentTurn(), recent);
  }

  private String action(String decision) {
    if (decision == null || decision.isBlank()) return null;
    try { return objectMapper.readTree(decision).path("action").asText(null); }
    catch (Exception ex) { return null; }
  }
}
