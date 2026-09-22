package com.interview.modules.interview.model;

public record InterviewTurnCache(
    Long id,
    Integer turnOrder,
    String question,
    String answer,
    String turnType,
    Long parentTurnId,
    String decision,
    InterviewTurnStatus status
) {}
