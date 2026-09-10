package com.interview.common.config;

import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import java.util.UUID;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.knowledge-base.vectorize.task")
public class KnowledgeBaseVectorizeTaskProperties {

  private boolean enabled = true;
  private String streamKey = AsyncTaskStreamConstants.KB_VECTORIZE_STREAM_KEY;
  private String consumerGroup = AsyncTaskStreamConstants.KB_VECTORIZE_GROUP;
  private String consumerName = "knowledgebase-vectorize-" + UUID.randomUUID();
  private int batchSize = 5;
}
