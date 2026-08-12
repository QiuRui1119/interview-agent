package com.interview.modules.resume.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "resume_analyses")
public class ResumeAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private ResumeEntity resumeEntity;

    // 总分 (0-100)
    private Integer overallScore;

    // 各维度评分
    private Integer contentScore;      // 内容完整性 (0-25)
    private Integer structureScore;    // 结构清晰度 (0-20)
    private Integer skillMatchScore;   // 技能匹配度 (0-25)
    private Integer expressionScore;   // 表达专业性 (0-15)
    private Integer projectScore;      // 项目经验 (0-15)

    //简历摘要
    @Column(columnDefinition = "TEXT")
    private String summary;

    // 优点列表
    @Column(columnDefinition = "TEXT")
    private String strengthsJson; // JSON格式存储优点列表

    //改进建议列表
    @Column(columnDefinition = "TEXT")
    private String suggestionsJson; // JSON格式存储改进建议列表

    //评测时间
    @Column(nullable = false)
    private Long analysisTime;

}
