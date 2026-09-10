package com.interview.modules.knowledgebase.model;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(name = "knowledge_bases", indexes = {
    @Index(name = "idx_kb_source_type", columnList = "source_type"),
    @Index(name = "idx_knowledge_base_category", columnList = "category")
}, uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_kb_source_hash",
        columnNames = {"source_type", "file_hash"})
})
public class KnowledgeBaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 文件内容的SHA-256哈希值，用于去重
  @Column(nullable = false, length = 64)
  private String fileHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 20)
  private KnowledgeSourceType sourceType = KnowledgeSourceType.USER_UPLOAD;

  @Column(name = "source_key", length = 500)
  private String sourceKey;

  @Column(name = "content_hash", length = 64)
  private String contentHash;

  @Column(nullable = false)
  private Integer version = 1;

  @Column(nullable = false)
  private Boolean enabled = true;

  // 知识库名称（用户自定义或从文件名提取）
  @Column(nullable = false)
  private String name;

  // 分类/分组（如"Java面试"、"项目文档"等）
  @Column(nullable = false, length = 100)
  private String category;

  // 原始文件名
  @Column(nullable = false)
  private String originalFileName;

  // 文件大小
  private Long fileSize;

  // 文件类型
  private String contentType;

  // minio存储的文件Key
  @Column(length = 500)
  private String storageKey;

  // minio存储的文件URL
  @Column(length = 1000)
  private String storageUrl;

  // 上传时间
  @Column(nullable = false)
  private LocalDateTime uploadTime;

  // 最后访问时间
  private LocalDateTime lastAccessedTime;

  // 访问次数
  @Column(nullable = false)
  private Integer accessCount = 0;

  // 问题数量（用户针对此知识库提问的次数）
  private Integer questionCount = 0;

  // 知识库向量化状态
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VectorStatus vectorStatus = VectorStatus.PENDING;

  // 知识库向量化失败的错误信息
  @Column(length = 1000)
  private String vectorError;

  // 向量分块数量
  private Integer vectorChunkCount = 0;

  @PrePersist
  protected void onCreate() {
    this.uploadTime = LocalDateTime.now();
    this.lastAccessedTime = LocalDateTime.now();
  }
}
