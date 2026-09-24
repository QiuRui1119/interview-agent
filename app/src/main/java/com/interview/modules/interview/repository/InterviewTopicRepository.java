package com.interview.modules.interview.repository;

import com.interview.modules.interview.model.InterviewTopicEntity;
import com.interview.modules.interview.model.InterviewTopicStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface InterviewTopicRepository extends JpaRepository<InterviewTopicEntity, Long> {
  long countBySession_SessionIdAndStatus(String sessionId, InterviewTopicStatus status);

  Optional<InterviewTopicEntity> findBySession_SessionIdAndTopicOrder(
      String sessionId, Integer topicOrder);

  List<InterviewTopicEntity> findBySession_SessionIdOrderByTopicOrderAsc(String sessionId);
}
