package com.interview.modules.knowledgebase.model;

import java.util.List;

public record KnowledgeBaseQueryRequest(
    List<Long> knowledgeBaseIds,
    String question,
    List<KnowledgeBaseQueryHistoryMessage> history
) {
  public KnowledgeBaseQueryRequest(List<Long> knowledgeBaseIds, String question) {
    this(knowledgeBaseIds, question, List.of());
  }
}
