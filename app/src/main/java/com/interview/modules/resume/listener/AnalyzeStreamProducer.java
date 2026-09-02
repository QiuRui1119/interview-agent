package com.interview.modules.resume.listener;

import com.interview.common.config.ResumeAnalysisTaskProperties;
import com.interview.infrastructure.redis.RedisService;
import com.interview.infrastructure.stream.AbstractStreamProducer;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AnalyzeStreamProducer extends AbstractStreamProducer {

  private final ResumeAnalysisTaskProperties resumeAnalysisTaskProperties;

  public AnalyzeStreamProducer(
      RedisService redisService,
      ResumeAnalysisTaskProperties resumeAnalysisTaskProperties
  ) {
    super(redisService);
    this.resumeAnalysisTaskProperties = resumeAnalysisTaskProperties;
  }

  @Override
  protected String streamKey() {
    return resumeAnalysisTaskProperties.getStreamKey();
  }

  public String publishAnalyzeTask(Long resumeId, String resumeText) {
    Objects.requireNonNull(resumeId, "resumeId must not be null");
    Objects.requireNonNull(resumeText, "resumeText must not be null");
    return publish(Map.of(
        AsyncTaskStreamConstants.RESUME_ANALYSIS_RESUME_ID_FIELD,
        String.valueOf(resumeId),
        AsyncTaskStreamConstants.RESUME_ANALYSIS_RESUME_TEXT_FIELD,
        resumeText));
  }
}
