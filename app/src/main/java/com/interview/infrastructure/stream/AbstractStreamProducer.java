package com.interview.infrastructure.stream;

import java.util.Map;
import java.util.HashMap;
import com.interview.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractStreamProducer {

  private final RedisService redisService;

  protected abstract String streamKey();

  protected String publish(Map<String, String> body) {
    try {
      Map<String, String> message = new HashMap<>(body);
      message.putIfAbsent(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0");
      String messageId = redisService.streamAdd(
          streamKey(), message, AsyncTaskStreamConstants.STREAM_MAX_LEN);
      log.info("Published stream message: streamKey={}, messageId={}", streamKey(), messageId);
      return messageId;
    } catch (Exception e) {
      log.error("Failed to publish stream message: streamKey={}, body={}, error={}", streamKey(), body, e.getMessage(), e);
      throw e;
    }
  }
}
