package com.interview.modules.knowledgebase.service;

import com.interview.infrastructure.file.FileHashService;
import com.interview.modules.knowledgebase.listener.VectorizeStreamProducer;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.interview.modules.knowledgebase.model.VectorStatus;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuiltInKnowledgeBootstrapService {

  private static final String RESOURCE_PATTERN = "classpath*:skills/**/*.md";
  private static final String SOURCE_PREFIX = "classpath:";

  private final KnowledgeBasePersistenceService persistenceService;
  private final FileHashService fileHashService;
  private final VectorizeStreamProducer vectorizeStreamProducer;

  @EventListener(ApplicationReadyEvent.class)
  public void synchronizeBuiltInKnowledge() {
    try {
      Resource[] resources = new PathMatchingResourcePatternResolver()
          .getResources(RESOURCE_PATTERN);
      Arrays.stream(resources)
          .sorted((left, right) -> sourceKey(left).compareTo(sourceKey(right)))
          .forEach(this::synchronizeResource);
      log.info("Built-in knowledge synchronization completed: resourceCount={}", resources.length);
    } catch (IOException ex) {
      log.error("Failed to scan built-in knowledge resources", ex);
    }
  }

  private void synchronizeResource(Resource resource) {
    String sourceKey = sourceKey(resource);
    try {
      byte[] content = readContent(resource);
      String contentHash = fileHashService.calculateHash(content);
      KnowledgeBaseEntity existing = persistenceService.findBuiltInBySourceKey(sourceKey)
          .orElse(null);

      if (existing == null) {
        KnowledgeBaseEntity created = persistenceService.saveBuiltInKnowledgeBase(
            sourceKey,
            resourceName(resource),
            category(sourceKey),
            resource.getFilename(),
            contentHash);
        publish(created, "created");
        return;
      }

      if (contentHash.equals(existing.getContentHash())) {
        if (existing.getVectorStatus() == VectorStatus.FAILED) {
          persistenceService.markPending(existing.getId());
          publish(existing, "retry");
          return;
        }
        log.debug("Built-in knowledge unchanged, skipping: sourceKey={}", sourceKey);
        return;
      }

      KnowledgeBaseEntity updated = persistenceService.updateBuiltInForNewVersion(
          existing, contentHash);
      publish(updated, "updated");
    } catch (Exception ex) {
      log.error("Failed to synchronize built-in knowledge: sourceKey={}", sourceKey, ex);
    }
  }

  private void publish(KnowledgeBaseEntity entity, String action) {
    String messageId = vectorizeStreamProducer.publish(entity.getId());
    log.info("Queued built-in knowledge vectorization: action={}, knowledgeBaseId={}, version={}, messageId={}",
        action, entity.getId(), entity.getVersion(), messageId);
  }

  private static byte[] readContent(Resource resource) throws IOException {
    try (InputStream inputStream = resource.getInputStream()) {
      return inputStream.readAllBytes();
    }
  }

  private static String sourceKey(Resource resource) {
    try {
      String location = resource.getURL().toString();
      int markerIndex = location.indexOf("/skills/");
      if (markerIndex >= 0) {
        return SOURCE_PREFIX + location.substring(markerIndex + 1);
      }
    } catch (IOException ignored) {
      // Fall back to the resource filename when a URL is unavailable.
    }
    return SOURCE_PREFIX + "skills/" + resource.getFilename();
  }

  private static String resourceName(Resource resource) {
    String filename = resource.getFilename();
    if (filename == null || filename.isBlank()) {
      return "Built-in knowledge";
    }
    int extensionIndex = filename.lastIndexOf('.');
    return extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
  }

  private static String category(String sourceKey) {
    int lastSlash = sourceKey.lastIndexOf('/');
    int previousSlash = sourceKey.lastIndexOf('/', lastSlash - 1);
    return previousSlash >= 0
        ? sourceKey.substring(previousSlash + 1, lastSlash)
        : "built-in";
  }
}
