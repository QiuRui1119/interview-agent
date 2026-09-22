package com.interview.modules.interview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "interview_topic_evidence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewTopicEvidenceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "topic_id", nullable = false)
  private InterviewTopicEntity topic;

  @Column(name = "knowledge_base_id", nullable = false)
  private Long knowledgeBaseId;

  @Column(name = "chunk_id", nullable = false, length = 255)
  private String chunkId;

  @Column(name = "source_name", length = 255)
  private String sourceName;

  @Column(name = "source_key", length = 500)
  private String sourceKey;

  @Column(name = "source_type", length = 20)
  private String sourceType;

  @Column(length = 100)
  private String category;

  private Integer version;
  private Double score;

  @Column(name = "content_snapshot", nullable = false, columnDefinition = "TEXT")
  private String contentSnapshot;

  public static InterviewTopicEvidenceEntity create(InterviewTopicEntity topic,
      Long knowledgeBaseId, String chunkId, String sourceName, String sourceKey,
      String sourceType, String category, Integer version, Double score, String content) {
    InterviewTopicEvidenceEntity entity = new InterviewTopicEvidenceEntity();
    entity.topic = topic;
    entity.knowledgeBaseId = knowledgeBaseId;
    entity.chunkId = chunkId;
    entity.sourceName = sourceName;
    entity.sourceKey = sourceKey;
    entity.sourceType = sourceType;
    entity.category = category;
    entity.version = version;
    entity.score = score;
    entity.contentSnapshot = content;
    return entity;
  }
}
