package com.interview.modules.resume.service;

import com.interview.common.config.AppConfigProperties;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.infrastructure.file.ContentTypeDetectionService;
import com.interview.infrastructure.file.FileStorageService;
import com.interview.infrastructure.file.FileValidationService;
import com.interview.infrastructure.file.TextCleaningService;
import com.interview.modules.resume.listener.AnalyzeStreamProducer;
import com.interview.modules.resume.model.ResumeAnalysisStatusResponse;
import com.interview.modules.resume.model.ResumeEntity;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeUploadService {

  // 简历文件最大 10MB。
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  private final FileValidationService fileValidationService;
  private final ContentTypeDetectionService contentTypeDetectionService;
  private final AppConfigProperties appConfigProperties;
  private final ResumePersistenceService resumePersistenceService;
  private final FileStorageService fileStorageService;
  private final ResumeParseService resumeParseService;
  private final TextCleaningService textCleaningService;
  private final AnalyzeStreamProducer analyzeStreamProducer;

  /**
   * 上传并分析简历。
   *
   * <p>当前流程为：文件校验 -> 去重 -> 文本解析 -> MinIO 存储 -> 简历入库 -> 发布异步分析任务。</p>
   *
   * @param file 上传的简历文件
   * @return 上传信息、存储信息、分析状态和分析结果
   */
  public Map<String, Object> uploadAndAnalyzeResume(MultipartFile file) {
    long startTime = System.currentTimeMillis();
    // 1. 校验文件大小和基础文件状态。
    fileValidationService.validateFile(file, MAX_FILE_SIZE, "resume");

    String fileName = file.getOriginalFilename();
    long fileSize = file.getSize();
    log.info("Received resume upload: filename={}, size={} bytes ({})",
        fileName, fileSize, formatFileSize(fileSize));

    // 2. 基于真实文件内容检测类型，避免只信任浏览器传入的 Content-Type。
    String detectedContentType = contentTypeDetectionService.detectContentType(file);
    fileValidationService.validateContentTypeByList(
        detectedContentType,
        appConfigProperties.getAllowedTypes(),
        "Unsupported resume file type: " + detectedContentType);

    // 3. 基于文件 hash 去重；重复上传时直接返回已有简历和最新分析结果。
    Optional<ResumeEntity> existingResume = resumePersistenceService.findExistingResume(file);
    if (existingResume.isPresent()) {
      log.info("Resume upload completed as duplicate: filename={}, duration={}ms",
          fileName, System.currentTimeMillis() - startTime);
      return handleDuplicateResume(existingResume.get());
    }

    // 4. 解析并清洗简历文本，供后续 AI 分析和原文展示使用。
    long parseStartTime = System.currentTimeMillis();
    String resumeText = resumeParseService.parseResumeContent(file);
    String cleanedResumeText = textCleaningService.cleanText(resumeText);
    if (cleanedResumeText.isEmpty()) {
      log.warn("Parsed resume text is empty: filename={}", fileName);
    } else {
      log.info("Parsed resume content: filename={}, duration={}ms, textLength={}",
          fileName, System.currentTimeMillis() - parseStartTime, cleanedResumeText.length());
    }

    // 5. 保存原始文件到对象存储，数据库只保存文件 key 和访问 URL。
    long storageStartTime = System.currentTimeMillis();
    String storageKey = fileStorageService.uploadResume(file);
    String storageUrl = fileStorageService.getFileUrl(storageKey);
    log.info("Stored resume file: key={}, duration={}ms",
        storageKey, System.currentTimeMillis() - storageStartTime);

    // 6. 保存简历基础信息到数据库。
    ResumeEntity resumeEntity = resumePersistenceService.saveNewResume(
        file, cleanedResumeText, storageKey, storageUrl);
    log.info("Saved resume metadata: resumeId={}", resumeEntity.getId());

    // 7. 发布自包含的异步分析任务，避免请求线程等待 AI 调用。
    try {
      String messageId = analyzeStreamProducer.publishAnalyzeTask(
          resumeEntity.getId(), cleanedResumeText);
      log.info("Queued resume analysis: resumeId={}, messageId={}",
          resumeEntity.getId(), messageId);
    } catch (Exception ex) {
      markAnalysisFailedQuietly(resumeEntity.getId(), ex);
      log.error("Failed to queue resume analysis: resumeId={}", resumeEntity.getId(), ex);
      throw new BusinessException(
          ErrorCode.RESUME_ANALYSIS_FAILED,
          "Failed to queue resume analysis",
          ex);
    }

    log.info("Resume upload completed and analysis queued: resumeId={}, duration={}ms",
        resumeEntity.getId(), System.currentTimeMillis() - startTime);

    return buildUploadResponse(
        resumeEntity,
        storageKey,
        storageUrl,
        AsyncTaskStatus.PENDING,
        false);
  }

  /**
   * 重新发布简历分析任务。
   */
  public ResumeAnalysisStatusResponse reanalyzeResume(Long resumeId) {
    String resumeText = resumePersistenceService.resetAnalysisForRetry(resumeId);
    try {
      String messageId = analyzeStreamProducer.publishAnalyzeTask(resumeId, resumeText);
      log.info("Requeued resume analysis: resumeId={}, messageId={}", resumeId, messageId);
      return new ResumeAnalysisStatusResponse(AsyncTaskStatus.PENDING, null);
    } catch (Exception ex) {
      markAnalysisFailedQuietly(resumeId, ex);
      log.error("Failed to requeue resume analysis: resumeId={}", resumeId, ex);
      throw new BusinessException(
          ErrorCode.RESUME_ANALYSIS_FAILED,
          "Failed to requeue resume analysis",
          ex);
    }
  }

  /**
   * 删除简历、分析记录以及对象存储中的原始文件。
   */
  public void deleteResume(Long resumeId) {
    String storageKey = resumePersistenceService.deleteResume(resumeId);
    if (storageKey == null || storageKey.isBlank()) {
      return;
    }

    try {
      fileStorageService.deleteResume(storageKey);
      log.info("Deleted resume file from storage: resumeId={}, storageKey={}",
          resumeId, storageKey);
    } catch (Exception ex) {
      log.error("Failed to delete resume file from storage: resumeId={}, storageKey={}",
          resumeId, storageKey, ex);
    }
  }

  /**
   * 分析失败时尽量记录失败状态，但不能让状态更新失败覆盖原始异常。
   */
  private void markAnalysisFailedQuietly(Long resumeId, Exception failure) {
    try {
      resumePersistenceService.markAnalysisFailed(resumeId, failure.getMessage());
    } catch (Exception statusUpdateFailure) {
      log.error("Failed to update resume analysis failure status: resumeId={}",
          resumeId, statusUpdateFailure);
    }
  }

  /**
   * 重复简历直接复用已有记录；如果已有分析结果，则一并返回。
   */
  private Map<String, Object> handleDuplicateResume(ResumeEntity existingResume) {
    log.info("Returning existing resume: resumeId={}", existingResume.getId());
    return buildUploadResponse(
        existingResume,
        existingResume.getStorageKey(),
        existingResume.getStorageUrl(),
        existingResume.getAnalyzeStatus(),
        true);
  }

  /**
   * 统一上传接口响应结构，避免新上传和重复上传返回字段不一致。
   */
  private Map<String, Object> buildUploadResponse(
      ResumeEntity resumeEntity,
      String storageKey,
      String storageUrl,
      AsyncTaskStatus analysisStatus,
      boolean duplicate
  ) {
    Map<String, Object> response = new java.util.LinkedHashMap<>();
    response.put("resume", Map.of(
        "id", resumeEntity.getId(),
        "fileName", resumeEntity.getOriginalFileName(),
        "analysisStatus", resolveStatus(analysisStatus).name()
    ));
    response.put("storage", Map.of(
        "fileKey", storageKey != null ? storageKey : "",
        "fileUrl", storageUrl != null ? storageUrl : "",
        "resumeId", resumeEntity.getId()
    ));
    response.put("duplicate", duplicate);
    return response;
  }

  /**
   * 分析状态为空时兜底为 PENDING。
   */
  private static AsyncTaskStatus resolveStatus(AsyncTaskStatus analysisStatus) {
    return analysisStatus != null ? analysisStatus : AsyncTaskStatus.PENDING;
  }

  /**
   * 将文件大小格式化为便于日志阅读的字符串。
   */
  private String formatFileSize(long bytes) {
    if (bytes < 1024) {
      return bytes + "B";
    }
    if (bytes < 1024 * 1024) {
      return String.format("%.1fKB", bytes / 1024.0);
    }
    return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
  }
}
