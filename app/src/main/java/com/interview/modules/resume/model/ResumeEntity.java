package com.interview.modules.resume.model;

import java.time.LocalDateTime;

import com.interview.common.model.AsyncTaskStatus;

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
import lombok.Data;

@Data
@Entity
@Table(name = "resumes", indexes = {
    @Index(name = "idx_resume_hash", columnList = "file_hash", unique = true)
})
public class ResumeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 文件的 hash 值，用于去重
  @Column(name = "file_hash", nullable = false, unique = true, length = 64)
  private String fileHash;

  @Column(name = "original_file_name", nullable = false)
  private String originalFileName;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "storage_key", length = 500)
  private String storageKey;

  @Column(name = "storage_url", length = 1000)
  private String storageUrl;

  @Column(name = "resume_text", columnDefinition = "TEXT")
  private String resumeText;

  @Column(name = "upload_time", nullable = false)
  private LocalDateTime uploadTime;

  // 最后访问时间
  @Column(name = "last_access_time")
  private LocalDateTime lastAccessTime;

  @Column(name = "access_count", nullable = false)
  private Integer accessCount = 0;

  @Enumerated(EnumType.STRING)
  @Column(name = "analyze_status", nullable = false, length = 20)
  private AsyncTaskStatus analyzeStatus = AsyncTaskStatus.PENDING;

  // 分析错误信息（失败时记录）
  @Column(name = "analyze_error", length = 500)
  private String analyzeError;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    uploadTime = now;
    lastAccessTime = now;
    accessCount = 1;
  }

  public void incrementAccessCount() {
    this.accessCount++;
    this.lastAccessTime = LocalDateTime.now();
  }

}
