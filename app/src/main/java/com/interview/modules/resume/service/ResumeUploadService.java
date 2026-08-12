package com.interview.modules.resume.service;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.interview.infrastructure.file.FileValidationService;
import com.interview.common.config.AppConfigProperties;
import com.interview.infrastructure.file.ContentTypeDetectionService;
import com.interview.modules.resume.model.ResumeEntity;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.modules.interview.model.ResumeAnalysisResponse;
import com.interview.infrastructure.file.FileStorageService;
import com.interview.infrastructure.file.TextCleaningService;


/**
 * 简历上传服务
 * 处理简历上传、解析的业务逻辑
 * AI 分析改为异步处理，通过 Redis Stream 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeUploadService {
    private final FileValidationService fileValidationService;
    private final ContentTypeDetectionService contentTypeDetectionService;
    private final AppConfigProperties appConfigProperties;
    private final ResumePersistenceService resumePersistenceService;
    private final FileStorageService fileStorageService;
    private final ResumeParseService resumeParseService;
    private final TextCleaningService textCleaningService;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB


    /**
     * 上传并分析简历（异步）
     *
     * @param file 简历文件
     * @return 上传结果（分析将异步进行）
     */
    public Map<String, Object> uploadAndAnalyzeResume(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        // 1. 验证文件类型和大小
        fileValidationService.validateFile(file, MAX_FILE_SIZE, "简历");

        String fileName = file.getOriginalFilename();
        Long fileSize = file.getSize();
        log.info("收到简历上传请求: {}, 大小: {} bytes ({}), 上传开始处理",
            fileName, fileSize, formatFileSize(fileSize));
        
        // 2. 检测文件内容类型
        String detectedContentType = contentTypeDetectionService.detectContentType(file);
        fileValidationService.validateContentTypeByList(
            detectedContentType,
            appConfigProperties.getAllowedTypes(),
             "不支持的文件类型: " + detectedContentType);

        //3. 检查简历是否已存在（去重）
        Optional<ResumeEntity> existingResume = resumePersistenceService.findExistingResume(file);
        if (existingResume.isPresent()) {
            log.info("简历上传处理完成（重复）: {} - 耗时: {}ms",
                fileName, System.currentTimeMillis() - startTime);
            log.info(existingResume.get().toString());
            return handleDuplicateResume(existingResume.get());
        }

        //4.解析简历文本内容
        Long parseStartTime = System.currentTimeMillis();
        String resumeText = resumeParseService.parseResumeContent(file);
        String cleanedResumeText = textCleaningService.cleanText(resumeText);
        if(cleanedResumeText == null || cleanedResumeText.isEmpty()) {
            log.warn("简历解析结果为空: {}", fileName);
        } else {
            log.info("简历解析完成: {} - 耗时: {}ms, 文本长度: {}",
                fileName, System.currentTimeMillis() - parseStartTime, cleanedResumeText.length());
        }
        // 5.保存简历到minIO
        Long storageStartTime = System.currentTimeMillis();
        String storageKey = fileStorageService.uploadResume(file);
        String storageUrl = fileStorageService.getFileUrl(storageKey);
        log.info("简历已存储到RustFS: {} - 存储耗时: {}ms", storageKey, System.currentTimeMillis() - storageStartTime);

        // 6.保存简历信息到数据库
        ResumeEntity resumeEntity = resumePersistenceService.saveNewResume(file,  cleanedResumeText, storageKey, storageUrl);
        log.info("简历信息已保存到数据库，ID: {} ", resumeEntity.getId());
        
        // 7.发送分析任务到 Redis Stream（异步处理）

        //8.返回上传结果
        return Map.of(
            "resume", Map.of(
                "id", resumeEntity.getId(),
                "fileName", resumeEntity.getOriginalFileName(),
                "analysisStatus", resumeEntity.getAnalyzeStatus()!=null?resumeEntity.getAnalyzeStatus():AsyncTaskStatus.PENDING.name()
            ),
            "storage",Map.of(
                "fileKey", storageKey,
                "fileUrl", storageUrl,
                "resumeId", resumeEntity.getId()
            ),
            "duplicate", false

        );
    }

    /**
     * 格式化文件大小为可读字符串
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 处理重复简历的情况
     *
     * @param existingResume 已存在的简历实体
     * @return 重复简历的处理结果
     */
    private Map<String, Object> handleDuplicateResume(ResumeEntity existingResume) {
        // 这里可以根据业务需求返回不同的响应，例如返回已存在的简历信息
        log.info("返回已存在的简历: {}", existingResume.getId());
        Optional<ResumeAnalysisResponse> analysisResult =
            resumePersistenceService.getLatestAnalysisResult(existingResume.getId());

        // 已有分析结果，直接返回
        // 没有分析结果（可能之前分析失败），返回当前状态
        return analysisResult.map(result -> Map.of(
           "analysisResult", result,
           "storage",Map.of(
               "fileKey", existingResume.getStorageKey()!=null?existingResume.getStorageKey():"",
               "fileUrl", existingResume.getStorageUrl()!=null?existingResume.getStorageUrl():"",
               "resumeId", existingResume.getId()
           ),
           "duplicate", true
        )).orElseGet(() -> Map.of(
            "resume", Map.of(
                "id", existingResume.getId(),
                "fileName", existingResume.getOriginalFileName(),
                "analysisStatus", existingResume.getAnalyzeStatus()!=null?existingResume.getAnalyzeStatus():AsyncTaskStatus.PENDING.name()
            ),
            "storage",Map.of(
                "fileKey", existingResume.getStorageKey()!=null?existingResume.getStorageKey():"",
                "fileUrl", existingResume.getStorageUrl()!=null?existingResume.getStorageUrl():"",
                "resumeId", existingResume.getId()
            ),
            "duplicate", true
        ));
    }

}
