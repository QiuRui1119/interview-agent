package com.interview.modules.interview.service;

import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewSessionStatus;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.interview.modules.interview.model.InterviewSessionCacheRefreshEvent;

@Service
@RequiredArgsConstructor
public class InterviewSessionPersistenceService {
  private final InterviewSessionRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public InterviewSessionEntity create(Long resumeId, String skillId, String difficulty,
      int topicCount, int maxFollowUps, int maxTurns, Set<Long> knowledgeBaseIds) {
    InterviewSessionEntity entity = InterviewSessionEntity.create();
    entity.configure(resumeId, skillId, difficulty, topicCount, maxFollowUps, maxTurns,
        knowledgeBaseIds);
    entity.transitionTo(InterviewSessionStatus.GENERATING);
    InterviewSessionEntity saved = repository.saveAndFlush(entity);
    eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(saved.getSessionId()));
    return saved;
  }
}
