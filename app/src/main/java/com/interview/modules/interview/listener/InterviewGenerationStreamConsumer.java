package com.interview.modules.interview.listener;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.redis.RedisService;
import com.interview.infrastructure.stream.AbstractStreamConsumer;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import com.interview.modules.interview.service.InterviewSessionReadService;
import com.interview.modules.interview.service.InterviewTopicGenerationService;
import java.util.Map;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

@Component
public class InterviewGenerationStreamConsumer extends AbstractStreamConsumer {
  private final InterviewTopicGenerationService generationService;
  private final InterviewSessionReadService readService;

  public InterviewGenerationStreamConsumer(RedisService redisService,
      InterviewTopicGenerationService generationService, InterviewSessionReadService readService) {
    super(redisService);
    this.generationService = generationService;
    this.readService = readService;
  }

  @Override protected boolean enabled() { return true; }
  @Override protected String streamKey() { return AsyncTaskStreamConstants.INTERVIEW_GENERATION_STREAM_KEY; }
  @Override protected String consumerGroup() { return AsyncTaskStreamConstants.INTERVIEW_GENERATION_GROUP; }
  @Override protected String consumerName() { return "interview-generation-"; }
  @Override protected int batchSize() { return 1; }
  @Override protected void markProcessing(Map<String, String> body) { parse(body); }

  @Override
  protected void handleMessage(StreamMessageId messageId, Map<String, String> body) {
    String sessionId = parse(body);
    generationService.generate(sessionId);
    readService.rebuild(sessionId);
  }

  @Override
  protected void handleMessageFailure(StreamMessageId id, Map<String, String> body, Exception failure) {
    String sessionId = parse(body);
    generationService.markFailed(sessionId, failure.getMessage());
    readService.rebuild(sessionId);
  }

  @Override protected void markCompleted(Map<String, String> body) {}

  private static String parse(Map<String, String> body) {
    String value = body == null ? null : body.get(AsyncTaskStreamConstants.FIELD_INTERVIEW_SESSION_ID);
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
          "Interview generation message is missing sessionId");
    }
    return value;
  }
}
