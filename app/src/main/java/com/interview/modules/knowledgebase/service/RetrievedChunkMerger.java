package com.interview.modules.knowledgebase.service;

import com.interview.modules.knowledgebase.model.RetrievedChunkDTO;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RetrievedChunkMerger {

  private RetrievedChunkMerger() {}

  public static List<RetrievedChunkDTO> merge(
      List<RetrievedChunkDTO> primary,
      List<RetrievedChunkDTO> secondary,
      int topK
  ) {
    Map<String, RetrievedChunkDTO> merged = new LinkedHashMap<>();
    primary.forEach(chunk -> mergeChunk(merged, chunk));
    secondary.forEach(chunk -> mergeChunk(merged, chunk));
    return merged.values().stream()
        .sorted(Comparator.comparing(
            RetrievedChunkDTO::score,
            Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(topK)
        .toList();
  }

  private static void mergeChunk(Map<String, RetrievedChunkDTO> merged, RetrievedChunkDTO chunk) {
    String key = chunk.chunkId() == null || chunk.chunkId().isBlank()
        ? chunk.sourceName() + "\n" + chunk.content()
        : chunk.chunkId();
    merged.merge(key, chunk, (left, right) -> score(left) >= score(right) ? left : right);
  }

  private static double score(RetrievedChunkDTO chunk) {
    return chunk.score() == null ? Double.NEGATIVE_INFINITY : chunk.score();
  }
}
