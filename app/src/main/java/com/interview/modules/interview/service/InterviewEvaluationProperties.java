package com.interview.modules.interview.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.interview.evaluation")
public class InterviewEvaluationProperties {
  private String systemPromptPath = "classpath:prompts/interview-evaluation-system.st";
  private String userPromptPath = "classpath:prompts/interview-evaluation-user.st";
  private int maxEvidencePerTopic = 3;
  private int maxEvidenceChars = 1800;
  private int maxPromptChars = 30000;
}
