package com.interview.modules.resume.service;

import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.resume.analysis")
public class ResumeAnalysisProperties {
    private String systemPromptPath;
    private String userPromptPath;
}
