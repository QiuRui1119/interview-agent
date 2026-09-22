package com.interview.modules.interview.model;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;

public class InvalidInterviewStateTransitionException extends BusinessException {

  public InvalidInterviewStateTransitionException(Enum<?> current, Enum<?> target) {
    super(
        ErrorCode.INVALID_INTERVIEW_STATE_TRANSITION,
        "Illegal interview state transition: " + current + " -> " + target);
  }
}
