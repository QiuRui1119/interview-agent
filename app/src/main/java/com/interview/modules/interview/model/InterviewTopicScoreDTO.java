package com.interview.modules.interview.model;

import java.util.List;

public record InterviewTopicScoreDTO(
    Long topicId,
    Integer topicOrder,
    String topicName,
    Integer score,
    String feedback,
    List<String> coveredPoints,
    List<String> missingPoints
) {}
