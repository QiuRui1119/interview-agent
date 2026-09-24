package com.interview.modules.interview.model;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewReportResponse(
    String sessionId,
    Integer overallScore,
    String overallFeedback,
    List<String> strengths,
    List<String> improvements,
    List<InterviewTopicScoreDTO> topicScores,
    List<InterviewTurnEvaluationDTO> turnEvaluations,
    LocalDateTime evaluatedAt
) {}
