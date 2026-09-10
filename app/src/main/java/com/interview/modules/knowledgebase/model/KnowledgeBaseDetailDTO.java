package com.interview.modules.knowledgebase.model;

import java.time.LocalDateTime;

public record KnowledgeBaseDetailDTO(
    Long id,
    String name,
    String category,
    String originalFileName,
    Long fileSize,
    String contentType,
    KnowledgeSourceType sourceType,
    String sourceKey,
    String contentHash,
    Integer version,
    Boolean enabled,
    VectorStatus vectorStatus,
    String vectorError,
    Integer vectorChunkCount,
    LocalDateTime uploadTime
) {}
