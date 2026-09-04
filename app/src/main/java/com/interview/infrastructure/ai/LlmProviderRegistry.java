package com.interview.infrastructure.ai;

import com.interview.common.config.LlmProviderProperties;
import com.interview.common.config.LlmProviderProperties.ProviderConfig;
import com.openai.core.Timeout;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmProviderRegistry {

  private static final double DEFAULT_TEMPERATURE = 0.2;
  private static final Duration AI_REQUEST_TIMEOUT = Duration.ofSeconds(120);

  private final LlmProviderProperties llmProviderProperties;
  private final Map<String, ChatClient> chatClientCache = new ConcurrentHashMap<>();

  public ChatClient getDefaultChatClient() {
    return getChatClient(llmProviderProperties.getDefaultProvider());
  }

  public ChatClient getChatClient(String providerId) {
    validateProviderId(providerId);
    String normalizedProviderId = providerId.trim();
    return chatClientCache.computeIfAbsent(normalizedProviderId, this::createChatClient);
  }

  private ChatClient createChatClient(String providerId) {
    ProviderConfig config = getProviderConfig(providerId);
    validateProviderConfig(providerId, config);

    log.info("Creating ChatClient: provider={}, baseUrl={}, model={}",
        providerId, config.getBaseUrl(), config.getModel());

    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .apiKey(config.getApiKey())
        .baseUrl(config.getBaseUrl())
        .model(config.getModel())
        .timeout(AI_REQUEST_TIMEOUT)
        .temperature(resolveTemperature(config))
        .build();

    OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .options(options)
        .httpClientBuilderCustomizer(builder -> builder.timeout(Timeout.builder()
            .connect(AI_REQUEST_TIMEOUT)
            .read(AI_REQUEST_TIMEOUT)
            .write(AI_REQUEST_TIMEOUT)
            .request(AI_REQUEST_TIMEOUT)
            .build()))
        .build();

    return ChatClient.builder(chatModel).build();
  }

  private ProviderConfig getProviderConfig(String providerId) {
    Map<String, ProviderConfig> providers = llmProviderProperties.getProviders();
    ProviderConfig config = providers != null ? providers.get(providerId) : null;
    if (config == null) {
      throw new IllegalArgumentException("Unknown LLM provider: " + providerId);
    }
    return config;
  }

  private static void validateProviderId(String providerId) {
    if (!StringUtils.hasText(providerId)) {
      throw new IllegalArgumentException("LLM provider id must not be blank");
    }
  }

  private static void validateProviderConfig(String providerId, ProviderConfig config) {
    if (!StringUtils.hasText(config.getBaseUrl())) {
      throw new IllegalArgumentException("LLM provider baseUrl must not be blank: " + providerId);
    }
    if (!StringUtils.hasText(config.getApiKey())) {
      throw new IllegalArgumentException("LLM provider apiKey must not be blank: " + providerId);
    }
    if (!StringUtils.hasText(config.getModel())) {
      throw new IllegalArgumentException("LLM provider model must not be blank: " + providerId);
    }
  }

  private static double resolveTemperature(ProviderConfig config) {
    return config.getTemperature() != null ? config.getTemperature() : DEFAULT_TEMPERATURE;
  }
}
