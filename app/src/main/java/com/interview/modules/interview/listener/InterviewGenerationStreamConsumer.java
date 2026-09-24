package com.interview.modules.interview.listener;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.redis.RedisService;
import com.interview.infrastructure.stream.AbstractStreamConsumer;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import com.interview.modules.interview.service.InterviewSessionService;
import com.interview.modules.interview.service.InterviewGenerationService;
import java.util.Map;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

@Component
public class InterviewGenerationStreamConsumer extends AbstractStreamConsumer {
  private final InterviewGenerationService generationService;
  private final InterviewSessionService sessionService;

  public InterviewGenerationStreamConsumer(RedisService redisService,
      InterviewGenerationService generationService, InterviewSessionService sessionService) {
    super(redisService);
    this.generationService = generationService;
    this.sessionService = sessionService;
  }

  @Override protected boolean enabled() { return true; }
  @Override protected String streamKey() { return AsyncTaskStreamConstants.INTERVIEW_GENERATION_STREAM_KEY; }
  @Override protected String consumerGroup() { return AsyncTaskStreamConstants.INTERVIEW_GENERATION_GROUP; }
  @Override protected String consumerName() { return "interview-generation-"; }
  @Override protected int batchSize() { return 1; }
  @Override
  protected void markProcessing(Map<String, String> body) {
    String sessionId = parse(body);
    generationService.markProcessing(sessionId);
    sessionService.rebuild(sessionId);
  }

  @Override
  protected void handleMessage(StreamMessageId messageId, Map<String, String> body) {
    String sessionId = parse(body);
    generationService.generate(sessionId);
    sessionService.rebuild(sessionId);
  }

  @Override
  protected void handleMessageFailure(StreamMessageId id, Map<String, String> body, Exception failure) {
    String sessionId = parse(body);
    generationService.markFailed(sessionId, failure.getMessage());
    sessionService.rebuild(sessionId);
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
