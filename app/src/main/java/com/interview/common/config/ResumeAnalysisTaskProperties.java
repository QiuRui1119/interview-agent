package com.interview.common.config;

import java.util.UUID;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.resume.analysis.task")
public class ResumeAnalysisTaskProperties {

  private boolean enabled = true;
  private String streamKey = AsyncTaskStreamConstants.RESUME_ANALYSIS_STREAM_KEY;
  private String consumerGroup = AsyncTaskStreamConstants.RESUME_ANALYSIS_CONSUMER_GROUP;
  private String consumerName =
      AsyncTaskStreamConstants.RESUME_ANALYSIS_CONSUMER_NAME_PREFIX + UUID.randomUUID();
  private int batchSize = 5;
  private long pollDelayMs = 1000;
}
