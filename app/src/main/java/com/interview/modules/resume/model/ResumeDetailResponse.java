package com.interview.modules.resume.model;

import com.interview.common.model.AsyncTaskStatus;
import java.time.LocalDateTime;

public record ResumeDetailResponse(
    Long id,
    String filename,
    Long fileSize,
    LocalDateTime uploadedAt,
    LocalDateTime lastAccessTime,
    Integer accessCount,
    String storageKey,
    String storageUrl,
    String resumeText,
    AsyncTaskStatus analyzeStatus,
    String analyzeError
) {
}
