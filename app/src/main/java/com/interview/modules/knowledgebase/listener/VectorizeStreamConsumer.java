package com.interview.modules.knowledgebase.listener;

import com.interview.common.config.KnowledgeBaseVectorizeTaskProperties;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.redis.RedisService;
import com.interview.infrastructure.stream.AbstractStreamConsumer;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.interview.modules.knowledgebase.model.VectorStatus;
import com.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.interview.modules.knowledgebase.repository.VectorRepository;
import com.interview.modules.knowledgebase.service.KnowledgeBasePersistenceService;
import com.interview.modules.knowledgebase.service.KnowledgeBaseVectorService;
import com.interview.modules.knowledgebase.service.KnowledgeContentService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VectorizeStreamConsumer extends AbstractStreamConsumer {

  private final KnowledgeBaseVectorizeTaskProperties properties;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeBasePersistenceService persistenceService;
  private final KnowledgeContentService knowledgeContentService;
  private final KnowledgeBaseVectorService vectorService;
  private final VectorRepository vectorRepository;
  private final ThreadLocal<String> activeJobId = new ThreadLocal<>();

  public VectorizeStreamConsumer(
      RedisService redisService,
      KnowledgeBaseVectorizeTaskProperties properties,
      KnowledgeBaseRepository knowledgeBaseRepository,
      KnowledgeBasePersistenceService persistenceService,
      KnowledgeContentService knowledgeContentService,
      KnowledgeBaseVectorService vectorService,
      VectorRepository vectorRepository) {
    super(redisService);
    this.properties = properties;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.persistenceService = persistenceService;
    this.knowledgeContentService = knowledgeContentService;
    this.vectorService = vectorService;
    this.vectorRepository = vectorRepository;
  }

  @Override
  protected boolean enabled() {
    return properties.isEnabled();
  }

  @Override
  protected String streamKey() {
    return properties.getStreamKey();
  }

  @Override
  protected String consumerGroup() {
    return properties.getConsumerGroup();
  }

  @Override
  protected String consumerName() {
    return properties.getConsumerName();
  }

  @Override
  protected int batchSize() {
    return properties.getBatchSize();
  }

  @Override
  protected void markProcessing(Map<String, String> body) {
    Long kbId = parseKnowledgeBaseId(body);
    Optional<KnowledgeBaseEntity> knowledgeBase = knowledgeBaseRepository.findById(kbId);
    if (knowledgeBase.isEmpty() || knowledgeBase.get().getVectorStatus() == VectorStatus.COMPLETED) {
      return;
    }
    persistenceService.markProcessing(kbId);
  }

  @Override
  protected void handleMessage(StreamMessageId messageId, Map<String, String> body) {
    Long kbId = parseKnowledgeBaseId(body);
    Optional<KnowledgeBaseEntity> knowledgeBaseOptional = knowledgeBaseRepository.findById(kbId);
    if (knowledgeBaseOptional.isEmpty()) {
      log.warn("Knowledge base was deleted, skipping vectorization: knowledgeBaseId={}", kbId);
      return;
    }

    KnowledgeBaseEntity knowledgeBase = knowledgeBaseOptional.get();
    if (knowledgeBase.getVectorStatus() == VectorStatus.COMPLETED) {
      log.info("Knowledge base vectorization already completed: knowledgeBaseId={}", kbId);
      return;
    }

    String cleanedText = knowledgeContentService.loadContent(knowledgeBase);

    String jobId = UUID.randomUUID().toString();
    activeJobId.set(jobId);
    int chunkCount = vectorService.vectorize(knowledgeBase, cleanedText, jobId);
    vectorRepository.promoteVectorJob(kbId, jobId);
    persistenceService.markCompleted(kbId, chunkCount);
    log.info("Knowledge base vectorization completed: knowledgeBaseId={}, chunkCount={}",
        kbId, chunkCount);
  }

  @Override
  protected void handleMessageFailure(
      StreamMessageId messageId,
      Map<String, String> body,
      Exception failure) {
    Long kbId = parseKnowledgeBaseId(body);
    log.error("Knowledge base vectorization failed: knowledgeBaseId={}, messageId={}",
        kbId, messageId, failure);
    String jobId = activeJobId.get();
    if (jobId != null) {
      try {
        vectorRepository.deleteByVectorJobId(jobId);
      } catch (Exception cleanupFailure) {
        log.error("Failed to clean temporary vectors: knowledgeBaseId={}, jobId={}",
            kbId, jobId, cleanupFailure);
      }
    }
    persistenceService.markFailed(kbId, failure.getMessage());
    activeJobId.remove();
  }

  @Override
  protected void markCompleted(Map<String, String> body) {
    // The completion status is saved together with the vector chunk count in handleMessage.
    activeJobId.remove();
  }

  private Long parseKnowledgeBaseId(Map<String, String> body) {
    String value = body == null ? null : body.get(AsyncTaskStreamConstants.FIELD_KB_ID);
    if (value == null || value.isBlank()) {
      throw new com.interview.common.exception.BusinessException(
          ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
          "Vectorization message is missing knowledgeBaseId");
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ex) {
      throw new com.interview.common.exception.BusinessException(
          ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
          "Vectorization message contains an invalid knowledgeBaseId",
          ex);
    }
  }

}
