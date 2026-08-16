package com.interview.modules.resume.model;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import lombok.Data;

@Data
@Entity
@Table(name = "resume_analyses")
public class ResumeAnalysisEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  // 所属简历。
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resume_id", nullable = false)
  private ResumeEntity resumeEntity;

  // 总分，范围 0-100。
  @Column(name = "overall_score")
  private Integer overallScore;

  // 内容完整性评分。
  @Column(name = "content_score")
  private Integer contentScore;

  // 结构清晰度评分。
  @Column(name = "structure_score")
  private Integer structureScore;

  // 技能匹配度评分。
  @Column(name = "skill_match_score")
  private Integer skillMatchScore;

  // 表达专业性评分。
  @Column(name = "expression_score")
  private Integer expressionScore;

  // 项目经验评分。
  @Column(name = "project_score")
  private Integer projectScore;

  // 简历整体摘要。
  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  // 优势列表，JSON 数组格式。
  @Column(name = "strengths_json", columnDefinition = "TEXT")
  private String strengthsJson;

  // 改进建议列表，JSON 数组格式。
  @Column(name = "suggestions_json", columnDefinition = "TEXT")
  private String suggestionsJson;

  // 分析完成时间
  @Column(name = "analysis_time", nullable = false)
  private LocalDateTime analysisTime;

  @PrePersist
  private void prePersist() {
    if (analysisTime == null) {
      analysisTime = LocalDateTime.now();
    }
  }
}
