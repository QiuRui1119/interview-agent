package com.interview.modules.knowledgebase.model;

public record RetrievedChunkDTO(
    String content,
    String sourceName,
    String sourceKey,
    String sourceType,
    String category,
    String chunkId,
    Double score
) {
  public RetrievedChunkDTO(
      String content,
      String sourceName,
      String sourceType,
      String category,
      String chunkId,
      Double score) {
    this(content, sourceName, null, sourceType, category, chunkId, score);
  }
}
