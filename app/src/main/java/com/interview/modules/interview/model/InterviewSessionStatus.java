package com.interview.modules.interview.model;

import java.util.Set;

public enum InterviewSessionStatus {
  CREATED, IN_PROGRESS, COMPLETED, ABORTED;

  public Set<InterviewSessionStatus> allowedTransitions() {
    return switch (this) {
      case CREATED -> Set.of(IN_PROGRESS, ABORTED);
      case IN_PROGRESS -> Set.of(COMPLETED, ABORTED);
      case COMPLETED, ABORTED -> Set.of();
    };
  }

  public boolean canTransitionTo(InterviewSessionStatus target) {
    return target != null && allowedTransitions().contains(target);
  }
}
