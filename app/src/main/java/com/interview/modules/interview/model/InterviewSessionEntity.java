// package com.interview.modules.interview.model;

// import org.springframework.web.bind.support.SessionStatus;

// import com.interview.modules.resume.model.ResumeEntity;

// import jakarta.persistence.Column;
// import jakarta.persistence.Entity;
// import jakarta.persistence.Enumerated;
// import jakarta.persistence.FetchType;
// import jakarta.persistence.GeneratedValue;
// import jakarta.persistence.GenerationType;
// import jakarta.persistence.Index;
// import jakarta.persistence.JoinColumn;
// import jakarta.persistence.ManyToOne;
// import jakarta.persistence.Table;
// import 

// @Entity
// @Table(name="interview_sessions",indexes={
//     @Index
// })
// public class InterviewSessionEntity {
//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;

//     // 会话ID (UUID)
//     @Column(nullable = false, unique = true, length = 36)
//     private String sessionId;

//     // 面试主题
//     @Column(length = 64)
//     private String skillId = "java-backend";

//     // 难度级别 (junior / mid / senior)
//     @Column(length = 16)
//     private String difficulty = "mid";

//     // 简历ID（直接映射FK列，避免LAZY加载触发额外查询）
//     @Column(name = "resume_id", insertable = false, updatable = false)
//     private Long resumeId;

//     // 关联的简历（可选，支持无简历通用面试）
//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "resume_id")
//     private ResumeEntity resume;

//     // 问题总数
//     private Integer totalQuestions;


//     // 当前问题索引
//     private Integer currentQuestionIndex = 0;

//     // 会话状态
//     @Enumerated(EnumType.STRING)
//     @Column(length = 20)
//     private SessionStatus status = SessionStatus.CREATED;

//     // 问题列表 (JSON格式)
//     @Column(columnDefinition = "TEXT")
//     private String questionsJson;

//     // 总分 (0-100)
//     private Integer overallScore;

//     // 总体评价
//     @Column(columnDefinition = "TEXT")
//     private String overallFeedback;

//     // 优势 (JSON)
//     @Column(columnDefinition = "TEXT")
//     private String strengthsJson;

//     // 改进建议 (JSON)
//     @Column(columnDefinition = "TEXT")
//     private String improvementsJson;

//     // 参考答案 (JSON)
//     @Column(columnDefinition = "TEXT")
//     private String referenceAnswersJson;
// }
