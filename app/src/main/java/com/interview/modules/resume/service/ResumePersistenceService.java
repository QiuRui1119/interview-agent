package com.interview.modules.resume.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.infrastructure.Mapper.ResumeMapper;
import com.interview.infrastructure.file.FileHashService;
import com.interview.modules.interview.model.ResumeAnalysisResponse;
import com.interview.modules.resume.model.ResumeAnalysisEntity;
import com.interview.modules.resume.model.ResumeDetailResponse;
import com.interview.modules.resume.model.ResumeEntity;
import com.interview.modules.resume.model.ResumeListItemDTO;
import com.interview.modules.resume.repository.ResumeAnalysisRepository;
import com.interview.modules.resume.repository.ResumeRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import org.springframework.data.domain.Sort;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumePersistenceService {

  // analyze_error 数据库字段长度为 500，写入前需要截断。
  private static final int MAX_ANALYSIS_ERROR_LENGTH = 500;

  private final ResumeRepository resumeRepository;
  private final FileHashService fileHashService;
  private final ResumeMapper resumeMapper;
  private final ResumeAnalysisRepository resumeAnalysisRepository;
  private final ObjectMapper objectMapper;

  /**
   * 检查简历是否已存在。
   *
   * <p>去重依据是文件内容 hash，而不是文件名；重复命中时顺便更新访问次数。</p>
   *
   * @param file 上传的简历文件
   * @return 已存在的简历记录，不存在则返回 empty
   */
  public Optional<ResumeEntity> findExistingResume(MultipartFile file) {
    try {
      String fileHash = fileHashService.calculateHash(file);
      Optional<ResumeEntity> existingResume = resumeRepository.findByFileHash(fileHash);
      if (existingResume.isPresent()) {
        log.info("Resume with hash {} already exists.", fileHash);
        existingResume.get().incrementAccessCount();
        resumeRepository.save(existingResume.get());
      }
      return existingResume;
    } catch (Exception ex) {
      log.error("Error checking existing resume", ex);
      return Optional.empty();
    }
  }

  /**
   * 保存新简历的基础信息。
   *
   * @param file 上传文件
   * @param resumeText 解析并清洗后的简历文本
   * @param storageKey 对象存储 key
   * @param storageUrl 对象存储访问 URL
   * @return 保存后的简历实体
   */
  @Transactional(rollbackOn = Exception.class)
  public ResumeEntity saveNewResume(
      MultipartFile file,
      String resumeText,
      String storageKey,
      String storageUrl
  ) {
    try {
      String fileHash = fileHashService.calculateHash(file);
      ResumeEntity resumeEntity = new ResumeEntity();
      resumeEntity.setFileHash(fileHash);
      resumeEntity.setOriginalFileName(file.getOriginalFilename());
      resumeEntity.setFileSize(file.getSize());
      resumeEntity.setContentType(file.getContentType());
      resumeEntity.setStorageKey(storageKey);
      resumeEntity.setStorageUrl(storageUrl);
      resumeEntity.setResumeText(resumeText);
      ResumeEntity savedResume = resumeRepository.save(resumeEntity);
      log.info("Saved new resume with ID: {}", savedResume.getId());
      return savedResume;
    } catch (Exception ex) {
      log.error("Error saving new resume", ex);
      throw new BusinessException(ErrorCode.RESUME_UPLOAD_FAILED, "Error saving new resume", ex);
    }
  }

  /**
   * 将简历分析状态标记为处理中。
   */
  @Transactional(rollbackOn = Exception.class)
  public void markAnalysisProcessing(Long resumeId) {
    updateAnalysisStatus(resumeId, AsyncTaskStatus.PROCESSING, null);
  }

  /**
   * 将简历分析状态标记为完成。
   */
  @Transactional(rollbackOn = Exception.class)
  public void markAnalysisCompleted(Long resumeId) {
    updateAnalysisStatus(resumeId, AsyncTaskStatus.COMPLETED, null);
  }

  /**
   * 保存 AI 分析结构。
   *
   * <p>strengths 和 suggestions 是列表结构，当前以 JSON 文本形式存入数据库。</p>
   *
   * @param resumeId 简历 ID
   * @param analysisResult AI 分析结果
   * @return 保存后重新组装的分析响应
   */
  @Transactional(rollbackOn = Exception.class)
  public ResumeAnalysisResponse saveAnalysisStructure(
      Long resumeId,
      ResumeAnalysisResponse analysisResult
  ) {
    if (analysisResult == null) {
      throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "Analysis result must not be null");
    }

    try {
      ResumeEntity resumeEntity = getResumeOrThrow(resumeId);
      ResumeAnalysisEntity analysisEntity = new ResumeAnalysisEntity();
      analysisEntity.setResumeEntity(resumeEntity);
      analysisEntity.setOverallScore(analysisResult.overallScore());
      applyScoreDetail(analysisEntity, analysisResult.scoreDetail());
      analysisEntity.setSummary(analysisResult.summary());
      analysisEntity.setStrengthsJson(toJson(analysisResult.strengths()));
      analysisEntity.setSuggestionsJson(toJson(analysisResult.suggestions()));
      analysisEntity.setAnalysisTime(LocalDateTime.now());

      ResumeAnalysisEntity savedAnalysis = resumeAnalysisRepository.save(analysisEntity);
      log.info("Saved resume analysis: resumeId={}, analysisId={}",
          resumeId, savedAnalysis.getId());
      return entityToDTO(savedAnalysis);
    } catch (JsonProcessingException ex) {
      log.error("Failed to serialize resume analysis result: resumeId={}", resumeId, ex);
      throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED,
          "Failed to save analysis structure", ex);
    }
  }

  /**
   * 保存分析结构并将简历标记为完成，两个数据库操作在同一个事务中执行。
   */
  @Transactional(rollbackOn = Exception.class)
  public ResumeAnalysisResponse saveAnalysisStructureAndComplete(
      Long resumeId,
      ResumeAnalysisResponse analysisResult
  ) {
    ResumeAnalysisResponse savedAnalysis = saveAnalysisStructure(resumeId, analysisResult);
    markAnalysisCompleted(resumeId);
    return savedAnalysis;
  }

  /**
   * 将简历分析状态标记为失败，并记录失败原因。
   */
  @Transactional(rollbackOn = Exception.class)
  public void markAnalysisFailed(Long resumeId, String errorMessage) {
    updateAnalysisStatus(resumeId, AsyncTaskStatus.FAILED, errorMessage);
  }

  /**
   * 更新简历分析状态。
   */
  private void updateAnalysisStatus(
      Long resumeId,
      AsyncTaskStatus status,
      String errorMessage
  ) {
    ResumeEntity resumeEntity = getResumeOrThrow(resumeId);
    resumeEntity.setAnalyzeStatus(status);
    resumeEntity.setAnalyzeError(
        status == AsyncTaskStatus.FAILED
            ? truncate(errorMessage, MAX_ANALYSIS_ERROR_LENGTH)
            : null);
    resumeRepository.save(resumeEntity);
  }

  /**
   * 获取某份简历最新一次分析结果。
   */
  @Transactional
  public Optional<ResumeAnalysisResponse> getLatestAnalysisResult(Long resumeId) {
    ResumeEntity resumeEntity = getResumeOrThrow(resumeId);
    return findLatestAnalysis(resumeEntity).map(this::entityToDTO);
  }

  /**
   * 获取简历历史列表及最新分析摘要。
   */
  @Transactional
  public List<ResumeListItemDTO> getResumeList() {
    List<ResumeEntity> resumes = resumeRepository.findAll(
        Sort.by(Sort.Direction.DESC, "uploadTime"));
    if (resumes.isEmpty()) {
      return List.of();
    }

    return resumes.stream()
        .map(resume -> toListItem(resume, findLatestAnalysis(resume).orElse(null)))
        .toList();
  }

  /**
   * 获取简历详情及全部分析记录。
   */
  @Transactional
  public ResumeDetailResponse getResumeDetail(Long resumeId) {
    ResumeEntity resumeEntity = getResumeOrThrow(resumeId);

    return new ResumeDetailResponse(
        resumeEntity.getId(),
        resumeEntity.getOriginalFileName(),
        resumeEntity.getFileSize(),
        resumeEntity.getUploadTime(),
        resumeEntity.getLastAccessTime(),
        resumeEntity.getAccessCount(),
        resumeEntity.getStorageKey(),
        resumeEntity.getStorageUrl(),
        resumeEntity.getResumeText(),
        resumeEntity.getAnalyzeStatus(),
        resumeEntity.getAnalyzeError());
  }

  /**
   * 删除简历及其全部分析记录。
   *
   * @param resumeId 简历 ID
   * @return 被删除简历的对象存储 key
   */
  @Transactional(rollbackOn = Exception.class)
  public String deleteResume(Long resumeId) {
    ResumeEntity resumeEntity = getResumeOrThrow(resumeId);
    resumeAnalysisRepository.deleteByResumeEntity(resumeEntity);
    resumeRepository.delete(resumeEntity);
    log.info("Deleted resume and analysis records: resumeId={}", resumeId);
    return resumeEntity.getStorageKey();
  }

  /**
   * 重置简历分析状态并返回自包含任务需要的简历文本，保留历史分析记录。
   */
  @Transactional(rollbackOn = Exception.class)
  public String resetAnalysisForRetry(Long resumeId) {
    ResumeEntity resumeEntity = getResumeOrThrow(resumeId);
    resumeEntity.setAnalyzeStatus(AsyncTaskStatus.PENDING);
    resumeEntity.setAnalyzeError(null);
    resumeRepository.save(resumeEntity);
    return resumeEntity.getResumeText();
  }

  /**
   * 将分析结果实体转换成接口返回对象。
   */
  public ResumeAnalysisResponse entityToDTO(ResumeAnalysisEntity entity) {
    try {
      List<String> strengths = objectMapper.readValue(
          entity.getStrengthsJson() != null ? entity.getStrengthsJson() : "[]",
          new TypeReference<>() {});

      List<ResumeAnalysisResponse.Suggestion> suggestions = objectMapper.readValue(
          entity.getSuggestionsJson() != null ? entity.getSuggestionsJson() : "[]",
          new TypeReference<>() {});

      return new ResumeAnalysisResponse(
          entity.getOverallScore(),
          resumeMapper.mapToScoreDetail(entity),
          entity.getSummary(),
          strengths,
          suggestions,
          entity.getResumeEntity().getResumeText()
      );
    } catch (JacksonException ex) {
      log.error("Failed to deserialize resume analysis result: analysisId={}",
          entity.getId(), ex);
      throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED,
          "Failed to read analysis result", ex);
    }
  }

  /**
   * 查询简历实体，不存在时抛业务异常。
   */
  private ResumeEntity getResumeOrThrow(Long resumeId) {
    return resumeRepository.findById(resumeId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.RESUME_NOT_FOUND,
            "Resume not found: " + resumeId));
  }

  private ResumeListItemDTO toListItem(
      ResumeEntity resumeEntity,
      ResumeAnalysisEntity latestAnalysis
  ) {
    return new ResumeListItemDTO(
        resumeEntity.getId(),
        resumeEntity.getOriginalFileName(),
        resumeEntity.getFileSize(),
        resumeEntity.getUploadTime(),
        resumeEntity.getAccessCount(),
        latestAnalysis != null ? latestAnalysis.getOverallScore() : null,
        latestAnalysis != null ? latestAnalysis.getAnalysisTime() : null,
        0,
        resumeEntity.getAnalyzeStatus(),
        resumeEntity.getAnalyzeError());
  }

  private Optional<ResumeAnalysisEntity> findLatestAnalysis(ResumeEntity resumeEntity) {
    return resumeAnalysisRepository.findByResumeEntityOrderByAnalysisTimeDesc(resumeEntity)
        .stream()
        .findFirst();
  }

  /**
   * 将分析结果中的各维度评分复制到实体字段。
   */
  private void applyScoreDetail(
      ResumeAnalysisEntity analysisEntity,
      ResumeAnalysisResponse.ScoreDetail scoreDetail
  ) {
    if (scoreDetail == null) {
      return;
    }

    analysisEntity.setContentScore(scoreDetail.contentScore());
    analysisEntity.setStructureScore(scoreDetail.structureScore());
    analysisEntity.setSkillMatchScore(scoreDetail.skillMatchScore());
    analysisEntity.setExpressionScore(scoreDetail.expressionScore());
    analysisEntity.setProjectScore(scoreDetail.projectScore());
  }

  /**
   * 将列表结构序列化为 JSON 字符串保存。
   */
  private String toJson(Object value) throws JsonProcessingException {
    return objectMapper.writeValueAsString(value != null ? value : List.of());
  }

  /**
   * 按数据库字段长度截断错误信息。
   */
  private static String truncate(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
