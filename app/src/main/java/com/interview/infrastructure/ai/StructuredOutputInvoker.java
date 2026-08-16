package com.interview.infrastructure.ai;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StructuredOutputInvoker {

  public <T> T invoke(
      ChatClient chatClient,
      String systemPrompt,
      String userPrompt,
      StructuredOutputConverter<T> outputConverter
  ) {
    validateRequest(chatClient, systemPrompt, userPrompt, outputConverter);

    try {
      T result = chatClient.prompt()
          .system(systemPrompt)
          .user(userPrompt)
          .call()
          .entity(outputConverter);

      if (result == null) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI structured output is empty");
      }
      return result;
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Failed to invoke AI structured output", ex);
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI structured output failed", ex);
    }
  }

  private static <T> void validateRequest(
      ChatClient chatClient,
      String systemPrompt,
      String userPrompt,
      StructuredOutputConverter<T> outputConverter
  ) {
    if (chatClient == null) {
      throw new IllegalArgumentException("chatClient must not be null");
    }
    if (!StringUtils.hasText(systemPrompt)) {
      throw new IllegalArgumentException("systemPrompt must not be blank");
    }
    if (!StringUtils.hasText(userPrompt)) {
      throw new IllegalArgumentException("userPrompt must not be blank");
    }
    if (outputConverter == null) {
      throw new IllegalArgumentException("outputConverter must not be null");
    }
  }
}
