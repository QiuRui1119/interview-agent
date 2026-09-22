package com.interview.modules.interview.model;

import java.util.Set;

public enum InterviewSessionStatus {
  CREATED, GENERATING, READY, IN_PROGRESS, COMPLETED, EVALUATED,
  GENERATION_FAILED, EVALUATION_FAILED, ABORTED;

  public Set<InterviewSessionStatus> allowedTransitions() {
    return switch (this) {
      case CREATED -> Set.of(GENERATING, ABORTED);
      case GENERATING -> Set.of(READY, GENERATION_FAILED, ABORTED);
      case GENERATION_FAILED -> Set.of(GENERATING, ABORTED);
      case READY -> Set.of(IN_PROGRESS, ABORTED);
      case IN_PROGRESS -> Set.of(COMPLETED, ABORTED);
      case COMPLETED -> Set.of(EVALUATED, EVALUATION_FAILED);
      case EVALUATION_FAILED -> Set.of(COMPLETED);
      case EVALUATED, ABORTED -> Set.of();
    };
  }

  public boolean canTransitionTo(InterviewSessionStatus target) {
    return target != null && allowedTransitions().contains(target);
  }
}
