package com.interview.modules.interview.repository;

import com.interview.modules.interview.model.InterviewSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {
  Optional<InterviewSessionEntity> findBySessionId(String sessionId);
}
