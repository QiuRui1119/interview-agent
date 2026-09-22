package com.interview.modules.interview.model;

import java.util.Set;

public enum InterviewTopicStatus {
  PENDING, ACTIVE, COMPLETED;

  public Set<InterviewTopicStatus> allowedTransitions() {
    return switch (this) {
      case PENDING -> Set.of(ACTIVE);
      case ACTIVE -> Set.of(COMPLETED);
      case COMPLETED -> Set.of();
    };
  }

  public boolean canTransitionTo(InterviewTopicStatus target) {
    return target != null && allowedTransitions().contains(target);
  }
}
