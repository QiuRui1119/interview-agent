package com.interview.modules.knowledgebase.model;

public record KnowledgeBaseQuerySourceDTO(
    String sourceName,
    String sourceKey,
    String sourceType,
    Double score,
    String chunkId
) {}
