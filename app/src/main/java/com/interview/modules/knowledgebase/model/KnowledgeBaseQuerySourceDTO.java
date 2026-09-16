package com.interview.modules.knowledgebase.model;

public record KnowledgeBaseQuerySourceDTO(
    String sourceName,
    String sourceType,
    String category,
    Integer chunkIndex,
    String sourceKey,
    Long knowledgeBaseId,
    Integer version,
    Double score,
    String chunkId
) {}
