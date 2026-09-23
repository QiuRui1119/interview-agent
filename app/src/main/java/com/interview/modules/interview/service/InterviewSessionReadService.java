package com.interview.modules.interview.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.interview.model.InterviewSessionCache;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewTopicStatus;
import com.interview.modules.interview.model.InterviewTurnCache;
import com.interview.modules.interview.model.InterviewTurnEntity;
import com.interview.modules.interview.model.InterviewTurnStatus;
import com.interview.modules.interview.model.InterviewRecentTurnCache;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.repository.InterviewTopicRepository;
import com.interview.modules.interview.repository.InterviewTurnRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewSessionReadService {

  private final InterviewSessionCacheService cacheService;
  private final InterviewSessionRepository sessionRepository;
  private final InterviewTopicRepository topicRepository;
  private final InterviewTurnRepository turnRepository;

  public InterviewSessionCache get(String sessionId) {
    return get(sessionId, null);
  }

  public InterviewSessionCache get(String sessionId, Long minimumVersion) {
    validateSessionId(sessionId);
    InterviewSessionCache cached = cacheService.get(sessionId);
    if (isVersionValid(cached, minimumVersion)) {
      return cached;
    }
    return rebuild(sessionId);
  }

  @Transactional(readOnly = true)
  public InterviewSessionCache rebuild(String sessionId) {
    validateSessionId(sessionId);
    InterviewSessionEntity session = sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    List<InterviewTurnEntity> recentTurns = turnRepository
        .findTop6BySession_SessionIdOrderByTurnOrderDesc(sessionId);
    InterviewTurnEntity currentTurn = resolveCurrentTurn(session, recentTurns);
    InterviewSessionCache rebuilt = new InterviewSessionCache(
        session.getSessionId(),
        session.getCurrentTurnId(),
        session.getCurrentTopicOrder(),
        session.getTotalTurns(),
        session.getStatus(),
        session.getGenerationStatus(),
        session.getVersion(),
        Instant.now(),
        Math.toIntExact(topicRepository.countBySession_SessionIdAndStatus(
            sessionId, InterviewTopicStatus.COMPLETED)),
        Math.toIntExact(turnRepository.countBySession_SessionIdAndStatus(
            sessionId, InterviewTurnStatus.DECIDED)),
        toCache(currentTurn),
        recentTurns.stream()
            .sorted(Comparator.comparing(InterviewTurnEntity::getTurnOrder))
            .map(InterviewSessionReadService::toRecentCache)
            .toList());
    cacheService.put(rebuilt);
    return rebuilt;
  }

  private InterviewTurnEntity resolveCurrentTurn(
      InterviewSessionEntity session,
      List<InterviewTurnEntity> recentTurns
  ) {
    if (session.getCurrentTurnId() == null) {
      return null;
    }
    return recentTurns.stream()
        .filter(turn -> session.getCurrentTurnId().equals(turn.getId()))
        .findFirst()
        .orElseGet(() -> turnRepository.findById(session.getCurrentTurnId()).orElse(null));
  }

  private static InterviewTurnCache toCache(InterviewTurnEntity turn) {
    return turn == null ? null : new InterviewTurnCache(
        turn.getId(), turn.getTurnOrder(), turn.getQuestion(), turn.getAnswer(),
        turn.getTurnType(), turn.getParentTurn() == null ? null : turn.getParentTurn().getId(),
        turn.getDecision(), turn.getStatus());
  }

  private static InterviewRecentTurnCache toRecentCache(InterviewTurnEntity turn) {
    return new InterviewRecentTurnCache(
        turn.getId(), turn.getQuestion(), turn.getAnswer(), turn.getDecision());
  }

  private static boolean isVersionValid(InterviewSessionCache cache, Long minimumVersion) {
    return cache != null && cache.version() != null
        && (minimumVersion == null || cache.version() >= minimumVersion);
  }

  private static void validateSessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Interview session id must not be blank");
    }
  }
}
