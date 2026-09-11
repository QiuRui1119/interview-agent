package com.interview.modules.knowledgebase.model;

import java.time.LocalDateTime;

public record KnowledgeBaseListItemDTO(
    Long id,
    String name,
    String category,
    String originalFileName,
    Long fileSize,
    KnowledgeSourceType sourceType,
    VectorStatus vectorStatus,
    String vectorError,
    Integer vectorChunkCount,
    LocalDateTime uploadTime,
    Boolean enabled
) {}
