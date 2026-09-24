package com.interview.modules.interview.model;

public record InterviewEvidenceSnapshotDTO(
    Long knowledgeBaseId,
    String chunkId,
    String sourceName,
    String sourceKey,
    String sourceType,
    String category,
    Integer version,
    Double score,
    String content
) {}
