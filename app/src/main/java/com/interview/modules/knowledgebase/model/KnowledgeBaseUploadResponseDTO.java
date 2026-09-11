package com.interview.modules.knowledgebase.model;

public record KnowledgeBaseUploadResponseDTO(
    Long id,
    String name,
    KnowledgeSourceType sourceType,
    VectorStatus vectorStatus,
    boolean duplicate
) {}
