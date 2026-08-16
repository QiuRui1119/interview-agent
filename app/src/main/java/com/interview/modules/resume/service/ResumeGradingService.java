package com.interview.modules.resume.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import com.interview.infrastructure.ai.StructuredOutputInvoker;
import com.interview.modules.interview.model.ResumeAnalysisResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ResumeGradingService {

  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<ResumeAnalysisResponseDTO> outputConverter;
  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;

  private record ResumeAnalysisResponseDTO(
      int overallScore,
      ScoreDetailDTO scoreDetail,
      String summary,
      List<String> strengths,
      List<SuggestionDTO> suggestions
  ) {}

  private record ScoreDetailDTO(
      int contentScore,
      int structureScore,
      int skillMatchScore,
      int expressionScore,
      int projectScore
  ) {}

  private record SuggestionDTO(
      String category,
      String priority,
      String issue,
      String recommendation
  ) {}

  public ResumeGradingService(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ResumeAnalysisProperties properties,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.structuredOutputInvoker = structuredOutputInvoker;
    this.systemPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getSystemPromptPath())
            .getContentAsString(StandardCharsets.UTF_8));
    this.userPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getUserPromptPath())
            .getContentAsString(StandardCharsets.UTF_8));
    this.outputConverter = new BeanOutputConverter<>(ResumeAnalysisResponseDTO.class);
  }

  public ResumeAnalysisResponse analyzeResume(String resumeText) {
    if (!StringUtils.hasText(resumeText)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Resume text must not be blank");
    }

    log.info("Analyzing resume, textLength={}", resumeText.length());

    try {
      String systemPrompt = systemPromptTemplate.render();
      String userPrompt = userPromptTemplate.render(Map.of("resumeText", resumeText));
      String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();
      ChatClient chatClient = llmProviderRegistry.getDefaultChatClient();

      ResumeAnalysisResponseDTO responseDTO = structuredOutputInvoker.invoke(
          chatClient,
          systemPromptWithFormat,
          userPrompt,
          outputConverter);

      return toResponse(responseDTO, resumeText);
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Error analyzing resume", ex);
      throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "Failed to analyze resume", ex);
    }
  }

  private static ResumeAnalysisResponse toResponse(
      ResumeAnalysisResponseDTO responseDTO,
      String originalText
  ) {
    return new ResumeAnalysisResponse(
        responseDTO.overallScore(),
        toScoreDetail(responseDTO.scoreDetail()),
        responseDTO.summary(),
        responseDTO.strengths() != null ? responseDTO.strengths() : List.of(),
        toSuggestions(responseDTO.suggestions()),
        originalText);
  }

  private static ResumeAnalysisResponse.ScoreDetail toScoreDetail(ScoreDetailDTO scoreDetailDTO) {
    if (scoreDetailDTO == null) {
      return new ResumeAnalysisResponse.ScoreDetail(0, 0, 0, 0, 0);
    }

    return new ResumeAnalysisResponse.ScoreDetail(
        scoreDetailDTO.contentScore(),
        scoreDetailDTO.structureScore(),
        scoreDetailDTO.skillMatchScore(),
        scoreDetailDTO.expressionScore(),
        scoreDetailDTO.projectScore());
  }

  private static List<ResumeAnalysisResponse.Suggestion> toSuggestions(
      List<SuggestionDTO> suggestions
  ) {
    if (suggestions == null) {
      return List.of();
    }

    return suggestions.stream()
        .map(ResumeGradingService::toSuggestion)
        .toList();
  }

  private static ResumeAnalysisResponse.Suggestion toSuggestion(SuggestionDTO suggestionDTO) {
    return new ResumeAnalysisResponse.Suggestion(
        suggestionDTO.category(),
        suggestionDTO.priority(),
        suggestionDTO.issue(),
        suggestionDTO.recommendation());
  }
}
