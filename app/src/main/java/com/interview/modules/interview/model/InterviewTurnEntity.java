package com.interview.modules.interview.model;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
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
@Table(name = "interview_turns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewTurnEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "topic_id", nullable = false)
  private InterviewTopicEntity topic;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private InterviewSessionEntity session;

  @Column(name = "turn_order", nullable = false)
  private Integer turnOrder;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String question;

  @Column(columnDefinition = "TEXT")
  private String answer;

  @Column(name = "turn_type", nullable = false, length = 30)
  private String turnType = "BASE";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_turn_id")
  private InterviewTurnEntity parentTurn;

  @Column(columnDefinition = "TEXT")
  private String decision;

  @Column(name = "request_id", length = 100)
  private String requestId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InterviewTurnStatus status = InterviewTurnStatus.CREATED;

  @Column(name = "answered_at")
  private LocalDateTime answeredAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  public static InterviewTurnEntity create(
      InterviewTopicEntity topic,
      int turnOrder,
      String question
  ) {
    if (topic == null || turnOrder < 0 || question == null || question.isBlank()) {
      throw new IllegalArgumentException("Interview turn requires topic, order and question");
    }
    InterviewTurnEntity entity = new InterviewTurnEntity();
    entity.topic = topic;
    entity.session = topic.getSession();
    entity.turnOrder = turnOrder;
    entity.question = question.strip();
    return entity;
  }

  public static InterviewTurnEntity createFollowUp(
      InterviewTopicEntity topic, int turnOrder, String question, InterviewTurnEntity parentTurn) {
    InterviewTurnEntity entity = create(topic, turnOrder, question);
    entity.turnType = "FOLLOW_UP";
    entity.parentTurn = parentTurn;
    return entity;
  }

  public void transitionTo(InterviewTurnStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new InvalidInterviewStateTransitionException(status, target);
    }
    if (target == InterviewTurnStatus.DECIDING && (answer == null || answer.isBlank())) {
      throw new BusinessException(
          ErrorCode.INTERVIEW_ANSWER_SAVE_FAILED,
          "Turn answer must be saved before decision starts");
    }
    status = target;
  }

  public void recordAnswer(String answer, String requestId) {
    if (status != InterviewTurnStatus.ANSWER_PENDING) {
      throw new InvalidInterviewStateTransitionException(status, InterviewTurnStatus.DECIDING);
    }
    if (answer == null || answer.isBlank()) {
      throw new IllegalArgumentException("Interview answer must not be blank");
    }
    this.answer = answer.strip();
    this.requestId = requestId;
    this.answeredAt = LocalDateTime.now();
  }

  public void recordAnswer(String answer) {
    recordAnswer(answer, null);
    startDeciding();
  }

  public void startDeciding() {
    transitionTo(InterviewTurnStatus.DECIDING);
  }

  public void recordDecision(String decision) {
    if (status != InterviewTurnStatus.DECIDING) {
      throw new InvalidInterviewStateTransitionException(status, InterviewTurnStatus.DECIDED);
    }
    this.decision = decision;
    transitionTo(InterviewTurnStatus.DECIDED);
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
