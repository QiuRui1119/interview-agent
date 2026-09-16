package com.interview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(Rewrite rewrite) {

  public RagProperties {
    rewrite = rewrite == null ? new Rewrite(true, 1200, 300, 10) : rewrite;
  }

  public record Rewrite(
      boolean enabled,
      int maxHistoryChars,
      int maxQuestionChars,
      int timeoutSeconds
  ) {
    public Rewrite {
      if (maxHistoryChars <= 0 || maxQuestionChars <= 0 || timeoutSeconds <= 0) {
        throw new IllegalArgumentException("RAG rewrite limits and timeout must be positive");
      }
    }
  }
}
