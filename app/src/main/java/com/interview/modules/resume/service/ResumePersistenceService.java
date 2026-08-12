package com.interview.modules.resume.service;

import java.util.Optional;
import java.util.List;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.interview.model.ResumeAnalysisResponse;
import com.interview.modules.resume.model.ResumeAnalysisEntity;
import com.interview.modules.resume.model.ResumeEntity;
import com.interview.modules.resume.repository.ResumeAnalysisRepository;
import com.interview.modules.resume.repository.ResumeRepository;
import com.interview.infrastructure.file.FileHashService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JacksonException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.interview.infrastructure.Mapper.ResumeMapper;

/**
 * 简历持久化服务
 * 简历和评测结果的持久化，简历删除时删除所有关联数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumePersistenceService {
    private final ResumeRepository resumeRepository;
    private final FileHashService fileHashService;
    private final ResumeMapper resumeMapper;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final ObjectMapper objectMapper;

    /**
     * 检查简历是否已存在（基于文件内容hash）
     * 
     * @param file 上传的文件
     * @return 如果存在返回已有的简历实体，否则返回空
     */
    public Optional<ResumeEntity> findExistingResume(MultipartFile file){
        try{
            String fileHash = fileHashService.calculateHash(file);
            Optional<ResumeEntity> existingResume = resumeRepository.findByFileHash(fileHash);
            if(existingResume.isPresent()){
                log.info("Resume with hash {} already exists.", fileHash);
                existingResume.get().incrementAccessCount();
                resumeRepository.save(existingResume.get());
            }
            return existingResume;
        }catch(Exception e){
            log.error("Error checking existing resume", e);
            return Optional.empty();
        }
    }

    /**
     * 保存新简历
     */
    @Transactional(rollbackOn = Exception.class)
    public ResumeEntity saveNewResume(MultipartFile file, String resumeText,String storageKey,String storageUrl ) {
        try{
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
        }catch(Exception e){
            log.error("Error saving new resume", e);
            throw new BusinessException(ErrorCode.RESUME_UPLOAD_FAILED, "Error saving new resume");
        }
    }


    /**
     * 获取简历的最新评测结果（返回DTO）
     */
    public Optional<ResumeAnalysisResponse> getLatestAnalysisResult(Long resumeId) {
        return Optional.ofNullable(
            resumeAnalysisRepository.findFirstByResumeEntityIdOrderByAnalysisTimeDesc(resumeId)
        ).map(this::entityToDTO);
    }



    public ResumeAnalysisResponse  entityToDTO(ResumeAnalysisEntity entity) {
        try {
            List<String> strengths = objectMapper.readValue(
                entity.getStrengthsJson() != null ? entity.getStrengthsJson() : "[]",
                    new TypeReference<>() {
                    }
            );
            
            List<ResumeAnalysisResponse.Suggestion> suggestions = objectMapper.readValue(
                entity.getSuggestionsJson() != null ? entity.getSuggestionsJson() : "[]",
                    new TypeReference<>() {
                    }
            );
            
            return new ResumeAnalysisResponse(
                entity.getOverallScore(),
                resumeMapper.mapToScoreDetail(entity),  // 使用MapStruct自动映射
                entity.getSummary(),
                strengths,
                suggestions,
                entity.getResumeEntity().getResumeText()
            );
        } catch (JacksonException e) {
            log.error("反序列化评测结果失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "获取评测结果失败");
        }
    }



}
