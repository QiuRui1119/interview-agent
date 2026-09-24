package com.interview.modules.interview.listener;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.redis.RedisService;
import com.interview.infrastructure.stream.AbstractStreamConsumer;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import com.interview.modules.interview.service.InterviewEvaluationService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InterviewEvaluationStreamConsumer extends AbstractStreamConsumer {
  private final InterviewEvaluationService evaluationService;

  public InterviewEvaluationStreamConsumer(RedisService redisService,
      InterviewEvaluationService evaluationService) {
    super(redisService);
    this.evaluationService = evaluationService;
  }

  @Override protected boolean enabled() { return true; }
  @Override protected String streamKey() {
    return AsyncTaskStreamConstants.INTERVIEW_EVALUATION_STREAM_KEY;
  }
  @Override protected String consumerGroup() {
    return AsyncTaskStreamConstants.INTERVIEW_EVALUATION_GROUP;
  }
  @Override protected String consumerName() { return "interview-evaluation-"; }
  @Override protected int batchSize() { return 1; }

  @Override
  protected void markProcessing(Map<String, String> body) {
    evaluationService.markProcessing(parseSessionId(body));
  }

  @Override
  protected void handleMessage(StreamMessageId messageId, Map<String, String> body) {
    String sessionId = parseSessionId(body);
    log.info("Starting interview evaluation: sessionId={}, retryCount={}", sessionId,
        body.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"));
    evaluationService.evaluate(sessionId);
  }

  @Override
  protected void handleMessageFailure(StreamMessageId messageId, Map<String, String> body,
      Exception failure) {
    String sessionId = parseSessionId(body);
    evaluationService.markFailed(sessionId, failure.getMessage());
    log.error("Interview evaluation failed: sessionId={}, messageId={}", sessionId, messageId,
        failure);
  }

  @Override protected void markCompleted(Map<String, String> body) {}

  private static String parseSessionId(Map<String, String> body) {
    String value = body == null ? null
        : body.get(AsyncTaskStreamConstants.FIELD_INTERVIEW_SESSION_ID);
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
          "Interview evaluation message is missing sessionId");
    }
    return value;
  }
}
