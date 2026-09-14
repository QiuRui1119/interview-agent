package com.interview.modules.knowledgebase.model;

import java.util.List;

public record RetrievalRequest(
    String query,
    List<Long> knowledgeBaseIds,
    int topK,
    double minScore
) {}
