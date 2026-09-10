package com.interview.infrastructure.ai;

import com.interview.common.config.LlmProviderProperties;
import com.interview.common.config.LlmProviderProperties.ProviderConfig;
import com.openai.core.Timeout;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmProviderRegistry {

  private static final double DEFAULT_TEMPERATURE = 0.2;
  private static final Duration AI_REQUEST_TIMEOUT = Duration.ofSeconds(120);
  private static final int DEFAULT_EMBEDDING_DIMENSIONS = 1024;
  private final LlmProviderProperties llmProviderProperties;
  private final Map<String, ChatClient> chatClientCache = new ConcurrentHashMap<>();
  private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

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

  // ==================== EmbeddingModel 相关 ====================

  /**
   * 获取默认的 Embedding Model（使用 default-embedding-provider 配置）
   */
  public EmbeddingModel getDefaultEmbeddingModel() {
    String defaultEmbeddingProvider = llmProviderProperties.getDefaultEmbeddingProvider();
    if (!StringUtils.hasText(defaultEmbeddingProvider)) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR,
          "Default embedding provider not configured. Please set app.ai.default-embedding-provider");
    }
    return getEmbeddingModel(defaultEmbeddingProvider);
  }

  public EmbeddingModel getEmbeddingModel(String providerId) {
    validateProviderId(providerId);
    String normalizedProviderId = providerId.trim();
    return embeddingModelCache.computeIfAbsent(normalizedProviderId, this::createEmbeddingModel);
  }

  private EmbeddingModel createEmbeddingModel(String providerId) {
    ProviderConfig config = getProviderConfig(providerId);
    validateEmbeddingConfig(providerId, config);

    log.info("Creating EmbeddingModel: provider={}, baseUrl={}, model={}, dimensions={}",
        providerId,
        config.getBaseUrl(),
        config.getEmbeddingModel(),
        config.getEmbeddingDimensions());

    OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
        .apiKey(config.getApiKey())
        .baseUrl(config.getBaseUrl())
        .model(config.getEmbeddingModel())
        .dimensions(config.getEmbeddingDimensions())
        .timeout(AI_REQUEST_TIMEOUT)
        .build();

    return OpenAiEmbeddingModel.builder()
        .options(options)
        .httpClientBuilderCustomizer(builder -> builder.timeout(Timeout.builder()
            .connect(AI_REQUEST_TIMEOUT)
            .read(AI_REQUEST_TIMEOUT)
            .write(AI_REQUEST_TIMEOUT)
            .request(AI_REQUEST_TIMEOUT)
            .build()))
        .build();
  }


  // ==================== 检验方法 ====================
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

  /**
   * 校验 Embedding 配置是否完整
   */
  private static void validateEmbeddingConfig(String providerId, ProviderConfig config) {
    // 1. 检查是否支持 Embedding
    if (config.getSupportsEmbedding() == null || !config.getSupportsEmbedding()) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR,
          "Provider " + providerId + " does not support embedding. " +
              "Please set supportsEmbedding=true in configuration.");
    }

    // 2. 检查 embeddingModel 是否配置
    if (!StringUtils.hasText(config.getEmbeddingModel())) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR,
          "Embedding model not configured for provider: " + providerId);
    }

    // 3. 检查 embeddingDimensions 是否为 1024
    Integer dimensions = config.getEmbeddingDimensions();
    if (dimensions == null || dimensions != DEFAULT_EMBEDDING_DIMENSIONS) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR,
          "Embedding dimensions for " + providerId + " must be " + DEFAULT_EMBEDDING_DIMENSIONS +
              ", but was: " + dimensions);
    }

    // 4. 复用基础配置校验（baseUrl、apiKey）
    validateProviderConfig(providerId, config);
  }

  /**
   * 清空所有缓存（Provider 配置变更后调用）
   */
  public void reload() {
    chatClientCache.clear();
    embeddingModelCache.clear();
    log.info("Cleared all LLM provider caches");
  }
}
