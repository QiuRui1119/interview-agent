package com.interview.infrastructure.stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import com.interview.infrastructure.redis.RedisService;
import org.redisson.api.stream.StreamMessageId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractStreamConsumer {

  private static final int MAX_RETRIES = 3;

  private final RedisService redisService;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private ExecutorService executorService;
  private String consumerName;

  protected abstract boolean enabled();

  protected abstract String streamKey();

  protected abstract String consumerGroup();

  protected abstract String consumerName();

  protected abstract int batchSize();

  protected abstract void markProcessing(Map<String, String> body);

  protected abstract void handleMessage(StreamMessageId messageId, Map<String, String> body);

  protected abstract void handleMessageFailure(
      StreamMessageId messageId,
      Map<String, String> body,
      Exception failure);

  protected abstract void markCompleted(Map<String, String> body);

  /**
   * 初始化消费者组，如果不存在则创建。
   * 使用线程池创建内部驱动的独立的消费者线程
   */
  @PostConstruct
  public void initializeConsumerGroup() {
    if (!enabled()) {
      log.info("Stream consumer disabled: streamKey={}, group={}", streamKey(), consumerGroup());
      return;
    }
    this.consumerName = consumerName() + UUID.randomUUID().toString().substring(0, 8);
    ;
    executorService = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        r -> new Thread(r, streamKey() + "-" + consumerGroup()),
        new ThreadPoolExecutor.AbortPolicy());
    running.set(true);
    executorService.submit(() -> {
      ensureConsumerGroup();
      while (running.get()) {
        try {
          pollStream();
        } catch (Exception e) {
          log.error("Error while polling stream: streamKey={}, group={}", streamKey(), consumerGroup(), e);
        }
      }
    });
  }

  @PreDestroy
  public void shutdownConsumer() {
    running.set(false);
    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  protected void pollStream() {
    if (!enabled()) {
      return;
    }

    redisService.streamConsumeMessages(
        streamKey(),
        consumerGroup(),
        consumerName,
        batchSize(),
        1000,
        5 * 60 * 1000,
        batchSize(),
        this::processMessage);
  }

  private void processMessage(
      StreamMessageId messageId,
      Map<String, String> body) {
    boolean acknowledge = true;
    try {
      markProcessing(body);
      handleMessage(messageId, body);
      markCompleted(body);
    } catch (Exception failure) {
      log.error("Failed to process stream message: streamKey={}, messageId={}",
          streamKey(), messageId, failure);
      int retryCount = retryCount(body);
      if (retryCount < MAX_RETRIES) {
        Map<String, String> retryBody = body == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        retryBody.put(AsyncTaskStreamConstants.FIELD_RETRY_COUNT,
            Integer.toString(retryCount + 1));
        try {
          String retryMessageId = redisService.streamAdd(
              streamKey(), retryBody, AsyncTaskStreamConstants.STREAM_MAX_LEN);
          log.warn("Requeued failed stream message: streamKey={}, messageId={}, retryMessageId={}, retryCount={}",
              streamKey(), messageId, retryMessageId, retryCount + 1);
        } catch (Exception retryFailure) {
          acknowledge = false;
          log.error("Failed to requeue stream message; leaving original pending: streamKey={}, messageId={}",
              streamKey(), messageId, retryFailure);
        }
      } else {
        log.error("Stream message exhausted retries: streamKey={}, messageId={}, retryCount={}",
            streamKey(), messageId, retryCount);
        try {
          handleMessageFailure(messageId, body, failure);
        } catch (Exception failureHandlerError) {
          log.error("Failed to handle terminal stream message failure: streamKey={}, messageId={}",
              streamKey(), messageId, failureHandlerError);
        }
      }
    } finally {
      if (acknowledge) {
        ackMessage(messageId);
        log.info("Acknowledged stream message: streamKey={}, messageId={}", streamKey(), messageId);
      }
    }
  }

  private static int retryCount(Map<String, String> body) {
    if (body == null) return MAX_RETRIES;
    try {
      return Math.max(0, Integer.parseInt(
          body.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0")));
    } catch (NumberFormatException ex) {
      return MAX_RETRIES;
    }
  }

  private void ensureConsumerGroup() {
    redisService.createStreamGroup(streamKey(), consumerGroup());
  }

  private void ackMessage(StreamMessageId messageId) {
    try {
      redisService.streamAck(streamKey(), consumerGroup(), messageId);
    } catch (Exception e) {
      log.error("Failed to ack stream message: messageId={}", messageId, e);
    }
  }
}
