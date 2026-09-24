package com.interview.modules.interview.model;

import com.interview.common.model.AsyncTaskStatus;
import java.time.LocalDateTime;

public record InterviewSessionListItemDTO(
    String sessionId,
    Long resumeId,
    String resumeFilename,
    String skillId,
    String difficulty,
    InterviewSessionStatus status,
    AsyncTaskStatus generationStatus,
    AsyncTaskStatus evaluateStatus,
    Integer overallScore,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {}
