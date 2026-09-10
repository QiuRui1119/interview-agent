package com.interview.modules.knowledgebase.listener;

import com.interview.infrastructure.redis.RedisService;
import com.interview.infrastructure.stream.AbstractStreamProducer;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class VectorizeStreamProducer extends AbstractStreamProducer {

  public VectorizeStreamProducer(RedisService redisService) {
    super(redisService);
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.KB_VECTORIZE_STREAM_KEY;
  }

  public String publish(Long knowledgeBaseId) {
    Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
    return publish(Map.of(
        AsyncTaskStreamConstants.FIELD_KB_ID,
        knowledgeBaseId.toString()));
  }
}
