package com.interview.modules.interview.model;

import com.interview.common.model.AsyncTaskStatus;

public record InterviewReportStatusResponse(
    AsyncTaskStatus status,
    String error,
    InterviewReportResponse report
) {}
