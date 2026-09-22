package com.interview.modules.interview.model;

public record InterviewAnswerResponse(
    String requestId,
    String action,
    Long nextTurnId,
    InterviewTurnStatus turnStatus,
    InterviewSessionStatus sessionStatus
) {}
