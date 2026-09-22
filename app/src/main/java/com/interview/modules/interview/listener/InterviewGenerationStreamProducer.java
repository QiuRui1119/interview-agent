package com.interview.modules.interview.listener;

import com.interview.infrastructure.redis.RedisService;
import com.interview.infrastructure.stream.AbstractStreamProducer;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InterviewGenerationStreamProducer extends AbstractStreamProducer {
  public InterviewGenerationStreamProducer(RedisService redisService) { super(redisService); }

  @Override protected String streamKey() {
    return AsyncTaskStreamConstants.INTERVIEW_GENERATION_STREAM_KEY;
  }

  public String publish(String sessionId) {
    return publish(Map.of(AsyncTaskStreamConstants.FIELD_INTERVIEW_SESSION_ID, sessionId));
  }
}
