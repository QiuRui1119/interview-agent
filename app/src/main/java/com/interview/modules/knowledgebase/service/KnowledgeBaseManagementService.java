package com.interview.modules.knowledgebase.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.file.FileStorageService;
import com.interview.modules.knowledgebase.listener.VectorizeStreamProducer;
import com.interview.modules.knowledgebase.model.KnowledgeBaseDetailDTO;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.interview.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import com.interview.modules.knowledgebase.model.KnowledgeBaseUploadResponseDTO;
import com.interview.modules.knowledgebase.model.KnowledgeSourceType;
import com.interview.modules.knowledgebase.model.VectorStatus;
import com.interview.modules.knowledgebase.repository.VectorRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseManagementService {

  private final KnowledgeBasePersistenceService persistenceService;
  private final VectorizeStreamProducer vectorizeStreamProducer;
  private final VectorRepository vectorRepository;
  private final FileStorageService fileStorageService;

  public List<KnowledgeBaseListItemDTO> findAll() {
    return persistenceService.findAll();
  }

  public KnowledgeBaseDetailDTO findById(Long kbId) {
    KnowledgeBaseEntity entity = getOrThrow(kbId);
    return new KnowledgeBaseDetailDTO(
        entity.getId(),
        entity.getName(),
        entity.getCategory(),
        entity.getOriginalFileName(),
        entity.getFileSize(),
        entity.getContentType(),
        entity.getSourceType(),
        entity.getSourceKey(),
        entity.getContentHash(),
        entity.getVersion(),
        entity.getEnabled(),
        entity.getVectorStatus(),
        entity.getVectorError(),
        entity.getVectorChunkCount(),
        entity.getUploadTime());
  }

  public KnowledgeBaseUploadResponseDTO revectorize(Long kbId) {
    KnowledgeBaseEntity entity = getOrThrow(kbId);
    if (entity.getVectorStatus() == VectorStatus.PROCESSING) {
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "Knowledge base is currently being vectorized: " + kbId);
    }

    persistenceService.markPending(kbId);
    try {
      vectorizeStreamProducer.publish(kbId);
    } catch (Exception ex) {
      persistenceService.markFailed(kbId, ex.getMessage());
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
          "Failed to queue knowledge base revectorization",
          ex);
    }

    return new KnowledgeBaseUploadResponseDTO(
        entity.getId(),
        entity.getName(),
        entity.getSourceType(),
        VectorStatus.PENDING);
  }

  public void delete(Long kbId) {
    KnowledgeBaseEntity entity = getOrThrow(kbId);
    if (entity.getVectorStatus() == VectorStatus.PROCESSING) {
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "Processing knowledge base cannot be deleted: " + kbId);
    }

    if (entity.getSourceType() == KnowledgeSourceType.BUILT_IN) {
      persistenceService.disable(kbId);
      log.info("Disabled built-in knowledge base: knowledgeBaseId={}", kbId);
      return;
    }

    if (entity.getStorageKey() != null && !entity.getStorageKey().isBlank()) {
      fileStorageService.deleteKnowledgeBase(entity.getStorageKey());
    }
    vectorRepository.deleteByKnowledgeBaseId(kbId);
    persistenceService.delete(kbId);
    log.info("Deleted uploaded knowledge base: knowledgeBaseId={}", kbId);
  }

  private KnowledgeBaseEntity getOrThrow(Long kbId) {
    return persistenceService.findById(kbId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.KNOWLEDGE_BASE_NOT_FOUND,
            "Knowledge base not found: " + kbId));
  }
}
