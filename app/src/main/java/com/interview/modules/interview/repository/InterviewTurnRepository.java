package com.interview.modules.interview.repository;

import com.interview.modules.interview.model.InterviewTurnEntity;
import com.interview.modules.interview.model.InterviewTurnStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurnEntity, Long> {
  long countBySession_SessionIdAndStatus(String sessionId, InterviewTurnStatus status);

  List<InterviewTurnEntity> findTop6BySession_SessionIdOrderByTurnOrderDesc(String sessionId);

  @EntityGraph(attributePaths = "topic")
  List<InterviewTurnEntity> findBySession_SessionIdOrderByTurnOrderAsc(String sessionId);

  Optional<InterviewTurnEntity> findBySession_SessionIdAndRequestId(String sessionId, String requestId);

  boolean existsBySession_SessionIdAndTurnOrderGreaterThan(String sessionId, Integer turnOrder);
}
