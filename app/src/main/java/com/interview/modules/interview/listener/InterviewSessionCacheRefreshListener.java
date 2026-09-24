package com.interview.modules.interview.listener;

import com.interview.modules.interview.model.InterviewSessionCacheRefreshEvent;
import com.interview.modules.interview.service.InterviewSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewSessionCacheRefreshListener {
  private final InterviewSessionService sessionService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void refresh(InterviewSessionCacheRefreshEvent event) {
    try {
      sessionService.rebuild(event.sessionId());
    } catch (Exception ex) {
      log.warn("Failed to refresh interview session cache after commit: sessionId={}",
          event.sessionId(), ex);
    }
  }
}
