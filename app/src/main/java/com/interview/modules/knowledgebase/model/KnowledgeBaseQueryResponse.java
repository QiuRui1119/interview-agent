package com.interview.modules.knowledgebase.model;

import java.util.List;

public record KnowledgeBaseQueryResponse(
    String answer,
    List<KnowledgeBaseQuerySourceDTO> sources
) {}
