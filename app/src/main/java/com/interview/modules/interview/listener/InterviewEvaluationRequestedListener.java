package com.interview.modules.interview.listener;

import com.interview.modules.interview.model.InterviewEvaluationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InterviewEvaluationRequestedListener {
  private final InterviewEvaluationStreamProducer producer;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEvaluationRequested(InterviewEvaluationRequestedEvent event) {
    producer.publish(event.sessionId());
  }
}
