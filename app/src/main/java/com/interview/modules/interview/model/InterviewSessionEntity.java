package com.interview.modules.interview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import jakarta.persistence.Convert;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "interview_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, unique = true, length = 36)
  private String sessionId;

  @Column(name = "resume_id")
  private Long resumeId;

  @Column(name = "skill_id", length = 100)
  private String skillId;

  @Column(length = 30)
  private String difficulty;

  @Column(nullable = false)
  private Integer topicCount = 0;

  @Column(nullable = false)
  private Integer maxFollowUpsPerTopic = 0;

  @Column(nullable = false)
  private Integer maxTurns = 0;

  @Column(name = "current_topic_order")
  private Integer currentTopicOrder;

  @Column(name = "current_turn_id")
  private Long currentTurnId;

  @Column(nullable = false)
  private Integer totalTurns = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private InterviewSessionStatus status = InterviewSessionStatus.CREATED;

  @Column(name = "status_reason", length = 1000)
  private String statusReason;

  @Column(name = "generation_status", length = 30)
  private String generationStatus = "PENDING";

  @Column(name = "generation_error", length = 1000)
  private String generationError;

  @Column(name = "evaluate_status", length = 30)
  private String evaluateStatus;

  @Column(name = "evaluate_error", length = 1000)
  private String evaluateError;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  @Convert(converter = LongSetJsonConverter.class)
  @Column(name = "knowledge_base_ids_json", nullable = false, columnDefinition = "TEXT")
  private Set<Long> knowledgeBaseIds = new LinkedHashSet<>();

  public static InterviewSessionEntity create() {
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.sessionId = UUID.randomUUID().toString();
    return entity;
  }

  public void configure(Long resumeId, String skillId, String difficulty, int topicCount,
      int maxFollowUpsPerTopic, int maxTurns, Set<Long> knowledgeBaseIds) {
    this.resumeId = resumeId;
    this.skillId = skillId;
    this.difficulty = difficulty;
    this.topicCount = topicCount;
    this.maxFollowUpsPerTopic = maxFollowUpsPerTopic;
    this.maxTurns = maxTurns;
    this.knowledgeBaseIds = new LinkedHashSet<>(knowledgeBaseIds);
  }

  public void markGenerationProcessing() {
    generationStatus = "PROCESSING";
  }

  public void markGenerationCompleted(Long firstTurnId) {
    generationStatus = "COMPLETED";
    generationError = null;
    currentTopicOrder = 0;
    currentTurnId = firstTurnId;
    totalTurns = 1;
    transitionTo(InterviewSessionStatus.READY);
  }

  public void markGenerationFailed(String error) {
    generationStatus = "FAILED";
    generationError = error == null ? null : error.substring(0, Math.min(1000, error.length()));
    failGeneration(error);
  }

  public void transitionTo(InterviewSessionStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new InvalidInterviewStateTransitionException(status, target);
    }
    status = target;
    if (target != InterviewSessionStatus.GENERATION_FAILED
        && target != InterviewSessionStatus.EVALUATION_FAILED
        && target != InterviewSessionStatus.ABORTED) {
      statusReason = null;
    }
  }

  public void failGeneration(String reason) {
    transitionTo(InterviewSessionStatus.GENERATION_FAILED);
    statusReason = normalizeReason(reason);
  }

  public void failEvaluation(String reason) {
    transitionTo(InterviewSessionStatus.EVALUATION_FAILED);
    statusReason = normalizeReason(reason);
  }

  public void abort(String reason) {
    transitionTo(InterviewSessionStatus.ABORTED);
    statusReason = normalizeReason(reason);
  }

  public void beginProgress() {
    if (status == InterviewSessionStatus.READY) {
      transitionTo(InterviewSessionStatus.IN_PROGRESS);
    }
  }

  public void moveToTurn(Long turnId, int topicOrder) {
    currentTurnId = turnId;
    currentTopicOrder = topicOrder;
    totalTurns++;
  }

  public void complete() {
    if (status == InterviewSessionStatus.IN_PROGRESS) {
      transitionTo(InterviewSessionStatus.COMPLETED);
      completedAt = LocalDateTime.now();
    }
  }

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (sessionId == null) {
      sessionId = UUID.randomUUID().toString();
    }
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  private static String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return null;
    }
    String normalized = reason.strip();
    return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
  }
}
