package com.interview.modules.knowledgebase.service;

import com.interview.common.config.AppConfigProperties;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.file.ContentTypeDetectionService;
import com.interview.infrastructure.file.FileHashService;
import com.interview.infrastructure.file.FileStorageService;
import com.interview.infrastructure.file.FileValidationService;
import com.interview.modules.knowledgebase.listener.VectorizeStreamProducer;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.interview.modules.knowledgebase.model.KnowledgeBaseUploadResponseDTO;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseUploadService {

  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  private final FileValidationService fileValidationService;
  private final ContentTypeDetectionService contentTypeDetectionService;
  private final AppConfigProperties appConfigProperties;
  private final FileHashService fileHashService;
  private final FileStorageService fileStorageService;
  private final KnowledgeBasePersistenceService persistenceService;
  private final VectorizeStreamProducer vectorizationProducer;

  public KnowledgeBaseUploadResponseDTO upload(
      MultipartFile file,
      String name,
      String category
  ) {
    fileValidationService.validateFile(file, MAX_FILE_SIZE, "knowledge base");
    validateText(name, "name");
    validateText(category, "category");

    String originalFileName = file.getOriginalFilename();
    if (originalFileName == null || originalFileName.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Original filename must not be blank");
    }

    String detectedContentType = contentTypeDetectionService.detectContentType(file);
    fileValidationService.validateContentTypeByList(
        detectedContentType,
        appConfigProperties.getAllowedTypes(),
        "Unsupported knowledge base file type: " + detectedContentType);

    String fileHash = fileHashService.calculateHash(file);
    Optional<KnowledgeBaseEntity> existing = persistenceService.findUploadedByFileHash(fileHash);
    if (existing.isPresent()) {
      KnowledgeBaseEntity existingEntity = existing.get();
      return new KnowledgeBaseUploadResponseDTO(
          existingEntity.getId(),
          existingEntity.getName(),
          existingEntity.getSourceType(),
          existingEntity.getVectorStatus(),
          true);
    }

    String storageKey = fileStorageService.uploadKnowledgeBase(file);
    String storageUrl = fileStorageService.getFileUrl(storageKey);

    KnowledgeBaseEntity entity;
    try {
      entity = persistenceService.saveUploadedKnowledgeBase(
          fileHash,
          name,
          category,
          originalFileName,
          file.getSize(),
          detectedContentType,
          storageKey,
          storageUrl,
          null);
    } catch (Exception ex) {
      deleteStorageQuietly(storageKey);
      throw ex;
    }

    try {
      String messageId = vectorizationProducer.publish(entity.getId());
      log.info("Queued knowledge base vectorization: knowledgeBaseId={}, messageId={}",
          entity.getId(), messageId);
    } catch (Exception ex) {
      log.error("Failed to queue knowledge base vectorization: knowledgeBaseId={}",
          entity.getId(), ex);
      try {
        persistenceService.markFailed(entity.getId(), ex.getMessage());
      } catch (Exception statusException) {
        log.error("Failed to mark knowledge base vectorization as failed: knowledgeBaseId={}",
            entity.getId(), statusException);
      }
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
          "Failed to queue knowledge base vectorization",
          ex);
    }

    return new KnowledgeBaseUploadResponseDTO(
        entity.getId(),
        entity.getName(),
        entity.getSourceType(),
        entity.getVectorStatus(),
        false);
  }

  private void deleteStorageQuietly(String storageKey) {
    try {
      fileStorageService.deleteKnowledgeBase(storageKey);
    } catch (Exception cleanupException) {
      log.error("Failed to clean up knowledge base file: storageKey={}", storageKey,
          cleanupException);
    }
  }

  private static void validateText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must not be blank");
    }
  }
}
