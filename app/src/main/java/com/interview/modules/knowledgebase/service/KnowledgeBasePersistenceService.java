package com.interview.modules.knowledgebase.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.interview.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import com.interview.modules.knowledgebase.model.KnowledgeSourceType;
import com.interview.modules.knowledgebase.model.VectorStatus;
import com.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBasePersistenceService {

  private static final int MAX_VECTOR_ERROR_LENGTH = 1000;

  private final KnowledgeBaseRepository knowledgeBaseRepository;

  /**
   * 保存用户上传的知识库基础信息。文件上传、文件解析和向量化必须在事务外完成。
   */
  @Transactional(rollbackOn = Exception.class)
  public KnowledgeBaseEntity saveUploadedKnowledgeBase(
      String fileHash,
      String name,
      String category,
      String originalFileName,
      Long fileSize,
      String contentType,
      String storageKey,
      String storageUrl,
      String contentHash
  ) {
    KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
    entity.setFileHash(fileHash);
    entity.setName(name);
    entity.setCategory(category);
    entity.setOriginalFileName(originalFileName);
    entity.setFileSize(fileSize);
    entity.setContentType(contentType);
    entity.setStorageKey(storageKey);
    entity.setStorageUrl(storageUrl);
    entity.setContentHash(contentHash);
    entity.setSourceType(KnowledgeSourceType.USER_UPLOAD);
    entity.setVectorStatus(VectorStatus.PENDING);
    entity.setVectorError(null);
    entity.setVectorChunkCount(0);

    KnowledgeBaseEntity saved = knowledgeBaseRepository.save(entity);
    log.info("Saved uploaded knowledge base: knowledgeBaseId={}", saved.getId());
    return saved;
  }

  @Transactional
  public Optional<KnowledgeBaseEntity> findById(Long kbId) {
    return knowledgeBaseRepository.findById(kbId);
  }

  @Transactional
  public Optional<KnowledgeBaseEntity> findUploadedByFileHash(String fileHash) {
    return knowledgeBaseRepository.findByFileHashAndSourceType(
        fileHash, KnowledgeSourceType.USER_UPLOAD);
  }

  @Transactional
  public Optional<KnowledgeBaseEntity> findBuiltInBySourceKey(String sourceKey) {
    return knowledgeBaseRepository.findBySourceTypeAndSourceKey(
        KnowledgeSourceType.BUILT_IN, sourceKey);
  }

  @Transactional(rollbackOn = Exception.class)
  public KnowledgeBaseEntity saveBuiltInKnowledgeBase(
      String sourceKey,
      String name,
      String category,
      String originalFileName,
      String contentHash
  ) {
    KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
    entity.setFileHash(contentHash);
    entity.setContentHash(contentHash);
    entity.setSourceType(KnowledgeSourceType.BUILT_IN);
    entity.setSourceKey(sourceKey);
    entity.setName(name);
    entity.setCategory(category);
    entity.setOriginalFileName(originalFileName);
    entity.setContentType("text/markdown");
    entity.setVectorStatus(VectorStatus.PENDING);
    entity.setVectorChunkCount(0);
    return knowledgeBaseRepository.save(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public KnowledgeBaseEntity updateBuiltInForNewVersion(
      KnowledgeBaseEntity entity,
      String contentHash
  ) {
    entity.setFileHash(contentHash);
    entity.setContentHash(contentHash);
    entity.setVersion(entity.getVersion() == null ? 2 : entity.getVersion() + 1);
    entity.setVectorStatus(VectorStatus.PENDING);
    entity.setVectorError(null);
    entity.setVectorChunkCount(0);
    return knowledgeBaseRepository.save(entity);
  }

  @Transactional
  public List<KnowledgeBaseListItemDTO> findAll() {
    return knowledgeBaseRepository.findAllByOrderByUploadTimeDesc()
        .stream()
        .map(this::toListItem)
        .toList();
  }

  @Transactional(rollbackOn = Exception.class)
  public void markPending(Long kbId) {
    KnowledgeBaseEntity entity = getKnowledgeBaseOrThrow(kbId);
    entity.setVectorStatus(VectorStatus.PENDING);
    entity.setVectorError(null);
    entity.setVectorChunkCount(0);
    knowledgeBaseRepository.save(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public void markProcessing(Long kbId) {
    KnowledgeBaseEntity entity = getKnowledgeBaseOrThrow(kbId);
    entity.setVectorStatus(VectorStatus.PROCESSING);
    entity.setVectorError(null);
    knowledgeBaseRepository.save(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public void markCompleted(Long kbId, Integer chunkCount) {
    KnowledgeBaseEntity entity = getKnowledgeBaseOrThrow(kbId);
    entity.setVectorStatus(VectorStatus.COMPLETED);
    entity.setVectorError(null);
    entity.setVectorChunkCount(chunkCount);
    knowledgeBaseRepository.save(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public void markFailed(Long kbId, String error) {
    KnowledgeBaseEntity entity = getKnowledgeBaseOrThrow(kbId);
    entity.setVectorStatus(VectorStatus.FAILED);
    entity.setVectorError(truncate(error, MAX_VECTOR_ERROR_LENGTH));
    knowledgeBaseRepository.save(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public void disable(Long kbId) {
    KnowledgeBaseEntity entity = getKnowledgeBaseOrThrow(kbId);
    entity.setEnabled(false);
    knowledgeBaseRepository.save(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public void delete(Long kbId) {
    KnowledgeBaseEntity entity = getKnowledgeBaseOrThrow(kbId);
    knowledgeBaseRepository.delete(entity);
    log.info("Deleted knowledge base: knowledgeBaseId={}", kbId);
  }

  private KnowledgeBaseEntity getKnowledgeBaseOrThrow(Long kbId) {
    return knowledgeBaseRepository.findById(kbId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.KNOWLEDGE_BASE_NOT_FOUND,
            "Knowledge base not found: " + kbId));
  }

  private KnowledgeBaseListItemDTO toListItem(KnowledgeBaseEntity entity) {
    return new KnowledgeBaseListItemDTO(
        entity.getId(),
        entity.getName(),
        entity.getCategory(),
        entity.getOriginalFileName(),
        entity.getSourceType(),
        entity.getVectorStatus(),
        entity.getVectorError(),
        entity.getVectorChunkCount(),
        entity.getUploadTime());
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
