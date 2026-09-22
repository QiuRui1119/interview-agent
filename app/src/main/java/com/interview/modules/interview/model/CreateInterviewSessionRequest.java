package com.interview.modules.interview.model;

import java.util.List;

public record CreateInterviewSessionRequest(
    Long resumeId,
    String skillId,
    String difficulty,
    List<Long> knowledgeBaseIds,
    Integer topicCount,
    Integer maxFollowUpsPerTopic,
    Integer maxTurns
) {}
