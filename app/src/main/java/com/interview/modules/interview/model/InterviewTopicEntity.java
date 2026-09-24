package com.interview.modules.interview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "interview_topics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewTopicEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private InterviewSessionEntity session;

  @Column(nullable = false, length = 100)
  private String category;

  @Column(name = "topic_name", nullable = false, length = 255)
  private String topicName;

  @Column(name = "topic_order", nullable = false)
  private Integer topicOrder;

  @Column(columnDefinition = "TEXT")
  private String objective;

  @Column(name = "retrieval_query", columnDefinition = "TEXT")
  private String retrievalQuery;

  @Column(name = "base_question", columnDefinition = "TEXT")
  private String baseQuestion;

  @Column(name = "fallback_follow_up", columnDefinition = "TEXT")
  private String fallbackFollowUp;

  @Column(name = "reference_answer", columnDefinition = "TEXT")
  private String referenceAnswer;

  @Column(name = "scoring_points_json", columnDefinition = "TEXT")
  private String scoringPointsJson;

  @Column(name = "evidence_snapshot_json", nullable = false, columnDefinition = "TEXT")
  private String evidenceSnapshotJson = "[]";

  @Column(name = "follow_up_count", nullable = false)
  private Integer followUpCount = 0;

  @Column(name = "max_follow_ups", nullable = false)
  private Integer maxFollowUps = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InterviewTopicStatus status = InterviewTopicStatus.PENDING;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  public static InterviewTopicEntity create(
      InterviewSessionEntity session,
      String category,
      String topicName,
      int topicOrder
  ) {
    if (session == null || category == null || category.isBlank()
        || topicName == null || topicName.isBlank() || topicOrder < 0) {
      throw new IllegalArgumentException("Interview topic requires session, category, name and order");
    }
    InterviewTopicEntity entity = new InterviewTopicEntity();
    entity.session = session;
    entity.category = category.strip();
    entity.topicName = topicName.strip();
    entity.topicOrder = topicOrder;
    return entity;
  }

  public void transitionTo(InterviewTopicStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new InvalidInterviewStateTransitionException(status, target);
    }
    status = target;
  }

  public void applyGeneratedContent(String objective, String retrievalQuery,
      String baseQuestion, String fallbackFollowUp, String referenceAnswer,
      String scoringPointsJson, String evidenceSnapshotJson, int maxFollowUps) {
    this.objective = objective;
    this.retrievalQuery = retrievalQuery;
    this.baseQuestion = baseQuestion;
    this.fallbackFollowUp = fallbackFollowUp;
    this.referenceAnswer = referenceAnswer;
    this.scoringPointsJson = scoringPointsJson;
    this.evidenceSnapshotJson = evidenceSnapshotJson == null ? "[]" : evidenceSnapshotJson;
    this.maxFollowUps = maxFollowUps;
  }

  public void incrementFollowUpCount() {
    if (followUpCount >= maxFollowUps) {
      throw new IllegalStateException("Topic follow-up limit reached");
    }
    followUpCount++;
  }

  public void complete() {
    transitionTo(InterviewTopicStatus.COMPLETED);
  }

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
