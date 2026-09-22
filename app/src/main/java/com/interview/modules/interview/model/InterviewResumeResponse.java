package com.interview.modules.interview.model;

import java.util.List;

public record InterviewResumeResponse(
    String sessionId,
    InterviewSessionStatus status,
    Integer currentTopicOrder,
    Integer completedTopicCount,
    Integer totalTurns,
    InterviewTurnCache currentTurn,
    List<InterviewRecentTurnCache> recentTurns
) {}
