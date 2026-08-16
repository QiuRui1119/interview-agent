package com.interview.modules.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.infrastructure.Mapper.ResumeMapper;
import com.interview.infrastructure.file.FileHashService;
import com.interview.modules.interview.model.ResumeAnalysisResponse;
import com.interview.modules.resume.model.ResumeAnalysisEntity;
import com.interview.modules.resume.model.ResumeEntity;
import com.interview.modules.resume.repository.ResumeAnalysisRepository;
import com.interview.modules.resume.repository.ResumeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("简历分析持久化服务")
class ResumePersistenceServiceTest {

  private ResumeRepository resumeRepository;
  private ResumeAnalysisRepository resumeAnalysisRepository;
  private ResumePersistenceService resumePersistenceService;

  @BeforeEach
  void setUp() {
    resumeRepository = mock(ResumeRepository.class);
    resumeAnalysisRepository = mock(ResumeAnalysisRepository.class);
    ResumeMapper resumeMapper = mock(ResumeMapper.class);

    when(resumeMapper.mapToScoreDetail(any(ResumeAnalysisEntity.class)))
        .thenAnswer(invocation -> {
          ResumeAnalysisEntity entity = invocation.getArgument(0);
          return new ResumeAnalysisResponse.ScoreDetail(
              entity.getContentScore(),
              entity.getStructureScore(),
              entity.getSkillMatchScore(),
              entity.getExpressionScore(),
              entity.getProjectScore());
        });

    resumePersistenceService = new ResumePersistenceService(
        resumeRepository,
        mock(FileHashService.class),
        resumeMapper,
        resumeAnalysisRepository,
        new ObjectMapper());
  }

  @Test
  @DisplayName("保存分析结果时写入评分和JSON字段并更新状态")
  void saveAnalysisResultPersistsScoresJsonAndStatus() {
    ResumeEntity resumeEntity = new ResumeEntity();
    resumeEntity.setId(1L);
    resumeEntity.setResumeText("original resume text");
    when(resumeRepository.findById(1L)).thenReturn(Optional.of(resumeEntity));
    when(resumeAnalysisRepository.save(any(ResumeAnalysisEntity.class)))
        .thenAnswer(invocation -> {
          ResumeAnalysisEntity entity = invocation.getArgument(0);
          entity.setId(7);
          return entity;
        });

    ResumeAnalysisResponse analysisResult = new ResumeAnalysisResponse(
        88,
        new ResumeAnalysisResponse.ScoreDetail(12, 13, 18, 9, 36),
        "summary",
        List.of("Java 基础扎实"),
        List.of(new ResumeAnalysisResponse.Suggestion(
            "项目",
            "高",
            "缺少量化指标",
            "补充 QPS 和响应时间优化前后对比")),
        "cleaned resume text");

    ResumeAnalysisResponse savedResult =
        resumePersistenceService.saveAnalysisResult(1L, analysisResult);

    ArgumentCaptor<ResumeAnalysisEntity> analysisCaptor =
        ArgumentCaptor.forClass(ResumeAnalysisEntity.class);
    verify(resumeAnalysisRepository).save(analysisCaptor.capture());
    ResumeAnalysisEntity savedEntity = analysisCaptor.getValue();

    assertThat(savedEntity.getResumeEntity()).isSameAs(resumeEntity);
    assertThat(savedEntity.getOverallScore()).isEqualTo(88);
    assertThat(savedEntity.getContentScore()).isEqualTo(12);
    assertThat(savedEntity.getStructureScore()).isEqualTo(13);
    assertThat(savedEntity.getSkillMatchScore()).isEqualTo(18);
    assertThat(savedEntity.getExpressionScore()).isEqualTo(9);
    assertThat(savedEntity.getProjectScore()).isEqualTo(36);
    assertThat(savedEntity.getStrengthsJson()).contains("Java 基础扎实");
    assertThat(savedEntity.getSuggestionsJson()).contains("缺少量化指标");
    assertThat(savedEntity.getAnalysisTime()).isNotNull();
    assertThat(resumeEntity.getAnalyzeStatus()).isEqualTo(AsyncTaskStatus.COMPLETED);
    assertThat(resumeEntity.getAnalyzeError()).isNull();
    assertThat(savedResult.originalText()).isEqualTo("original resume text");
  }
}
