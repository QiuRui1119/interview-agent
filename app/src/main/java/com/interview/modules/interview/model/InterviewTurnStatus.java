package com.interview.modules.interview.model;

import java.util.Set;

public enum InterviewTurnStatus {
  CREATED, ANSWER_PENDING, DECIDING, DECIDED;

  public Set<InterviewTurnStatus> allowedTransitions() {
    return switch (this) {
      case CREATED -> Set.of(ANSWER_PENDING);
      case ANSWER_PENDING -> Set.of(DECIDING);
      case DECIDING -> Set.of(DECIDED);
      case DECIDED -> Set.of();
    };
  }

  public boolean canTransitionTo(InterviewTurnStatus target) {
    return target != null && allowedTransitions().contains(target);
  }
}
