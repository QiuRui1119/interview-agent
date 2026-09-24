package com.interview.modules.interview.model;

import com.interview.common.model.AsyncTaskStatus;
import java.time.Instant;
import java.util.List;

public record InterviewSessionCache(
    String sessionId,
    Long currentTurnId,
    Integer currentTopicOrder,
    Integer totalTurns,
    InterviewSessionStatus status,
    AsyncTaskStatus generationStatus,
    AsyncTaskStatus evaluateStatus,
    String evaluateError,
    Long version,
    Instant cachedAt,
    Integer completedTopicCount,
    Integer completedTurnCount,
    InterviewTurnCache currentTurn,
    List<InterviewRecentTurnCache> recentTurns
) {
  public InterviewSessionCache {
    recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
  }
}
