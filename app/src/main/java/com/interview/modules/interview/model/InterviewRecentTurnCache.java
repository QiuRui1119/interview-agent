package com.interview.modules.interview.model;

public record InterviewRecentTurnCache(
    Long id,
    String question,
    String answer,
    String decision
) {}
