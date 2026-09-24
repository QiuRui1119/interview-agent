package com.interview.modules.interview.model;

import java.util.List;

public record InterviewTurnEvaluationDTO(
    Long turnId,
    Integer turnOrder,
    Long topicId,
    String question,
    String answer,
    String turnType,
    Long parentTurnId,
    Integer score,
    List<String> coveredPoints,
    List<String> missingPoints,
    String feedback
) {}
