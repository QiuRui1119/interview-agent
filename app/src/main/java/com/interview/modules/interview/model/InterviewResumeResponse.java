package com.interview.modules.interview.model;

import com.interview.common.model.AsyncTaskStatus;
import java.util.List;

public record InterviewResumeResponse(
    String sessionId,
    InterviewSessionStatus status,
    Integer currentTopicOrder,
    Integer completedTopicCount,
    Integer totalTurns,
    AsyncTaskStatus generationStatus,
    AsyncTaskStatus evaluateStatus,
    String evaluateError,
    InterviewTurnCache currentTurn,
    List<InterviewRecentTurnCache> recentTurns
) {}
