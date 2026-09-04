package com.interview.modules.resume.model;

import com.interview.common.model.AsyncTaskStatus;

public record ResumeAnalysisStatusResponse(
    AsyncTaskStatus status,
    String error
) {
}
