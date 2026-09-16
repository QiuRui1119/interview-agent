package com.interview.modules.knowledgebase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import com.interview.infrastructure.ai.PromptSanitizer;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.interview.modules.knowledgebase.model.KnowledgeBaseQueryRequest;
import com.interview.modules.knowledgebase.model.KnowledgeBaseQueryHistoryMessage;
import com.interview.modules.knowledgebase.model.KnowledgeBaseQueryResponse;
import com.interview.modules.knowledgebase.model.KnowledgeBaseQuerySourceDTO;
import com.interview.modules.knowledgebase.model.RetrievalRequest;
import com.interview.modules.knowledgebase.model.RetrievedChunkDTO;
import com.interview.modules.knowledgebase.model.VectorStatus;
import com.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class KnowledgeBaseQueryService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseQueryService.class);

  private static final int DEFAULT_TOP_K = 6;
  private static final double DEFAULT_MIN_SCORE = 0.3;
  private static final String NO_RELEVANT_INFORMATION = "知识库中没有相关信息";
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeRetrievalService knowledgeRetrievalService;
  private final QueryRewriteService queryRewriteService;
  private final LlmProviderRegistry llmProviderRegistry;
  private final ObjectMapper objectMapper;
  private final PromptSanitizer promptSanitizer;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;

  public KnowledgeBaseQueryService(
      KnowledgeBaseRepository knowledgeBaseRepository,
      KnowledgeRetrievalService knowledgeRetrievalService,
      QueryRewriteService queryRewriteService,
      LlmProviderRegistry llmProviderRegistry,
      ObjectMapper objectMapper,
      PromptSanitizer promptSanitizer,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.knowledgeRetrievalService = knowledgeRetrievalService;
    this.queryRewriteService = queryRewriteService;
    this.llmProviderRegistry = llmProviderRegistry;
    this.objectMapper = objectMapper;
    this.promptSanitizer = promptSanitizer;
    this.systemPromptTemplate = new PromptTemplate(resourceLoader
        .getResource("classpath:prompts/knowledgebase-query-system.st")
        .getContentAsString(StandardCharsets.UTF_8));
    this.userPromptTemplate = new PromptTemplate(resourceLoader
        .getResource("classpath:prompts/knowledgebase-query-user.st")
        .getContentAsString(StandardCharsets.UTF_8));
  }

  public KnowledgeBaseQueryResponse query(KnowledgeBaseQueryRequest request) {
    PreparedQuery preparedQuery = prepareQuery(request);
    if (preparedQuery.chunks().isEmpty()) {
      return new KnowledgeBaseQueryResponse(NO_RELEVANT_INFORMATION, List.of());
    }

    try {
      ChatClient chatClient = llmProviderRegistry.getDefaultChatClient();
      String answer = chatClient.prompt()
          .system(preparedQuery.systemPrompt())
          .user(preparedQuery.userPrompt())
          .call()
          .content();
      if (answer == null || answer.isBlank()) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "RAG answer is empty");
      }
      return new KnowledgeBaseQueryResponse(answer, toSources(preparedQuery.chunks()));
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Failed to generate knowledge base answer", ex);
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Failed to generate RAG answer", ex);
    }
  }

  /**
   * 校验和检索在返回 Flux 前同步完成，订阅后只有大模型输出是流式的。
   */
  public Flux<ServerSentEvent<String>> queryStream(KnowledgeBaseQueryRequest request) {
    PreparedQuery preparedQuery = prepareQuery(request);
    List<KnowledgeBaseQuerySourceDTO> sources = toSources(preparedQuery.chunks());
    ServerSentEvent<String> sourcesEvent = event("sources", toJson(sources));
    ServerSentEvent<String> completeEvent = event("complete", "true");

    if (preparedQuery.chunks().isEmpty()) {
      return Flux.just(
          event("text", NO_RELEVANT_INFORMATION),
          sourcesEvent,
          completeEvent);
    }

    ChatClient chatClient = llmProviderRegistry.getDefaultChatClient();
    return chatClient.prompt()
        .system(preparedQuery.systemPrompt())
        .user(preparedQuery.userPrompt())
        .stream()
        .content()
        .filter(content -> content != null && !content.isEmpty())
        .map(content -> event("text", content))
        .concatWithValues(sourcesEvent, completeEvent)
        .onErrorResume(ex -> {
          log.error("Failed to stream knowledge base answer", ex);
          return Flux.just(
              event("error", "知识库问答生成失败"),
              event("complete", "false"));
        });
  }

  private PreparedQuery prepareQuery(KnowledgeBaseQueryRequest request) {
    List<Long> knowledgeBaseIds = validateAndNormalize(request);
    validateKnowledgeBases(knowledgeBaseIds);
    String question = promptSanitizer.sanitize(request.question());
    List<RetrievedChunkDTO> originalChunks = retrieve(question, knowledgeBaseIds);
    List<Message> history = toMessages(request.history());
    List<RetrievedChunkDTO> chunks = originalChunks;
    String answerQuestion = question;
    if (queryRewriteService.shouldRewrite(question, history, originalChunks.isEmpty())) {
      String rewrittenQuestion = queryRewriteService.rewrite(question, history);
      answerQuestion=rewrittenQuestion;
      if (!rewrittenQuestion.equals(question)) {
        List<RetrievedChunkDTO> rewrittenChunks = retrieve(rewrittenQuestion, knowledgeBaseIds);
        chunks = RetrievedChunkMerger.merge(rewrittenChunks, originalChunks, DEFAULT_TOP_K);
        log.info("RAG query rewrite applied: originalResults={}, rewrittenResults={}, mergedResults={}",
            originalChunks.size(), rewrittenChunks.size(), chunks.size());
      }
    }
    return new PreparedQuery(
        chunks,
        systemPromptTemplate.render(),
        buildUserPrompt(answerQuestion, chunks));
  }

  private List<RetrievedChunkDTO> retrieve(String query, List<Long> knowledgeBaseIds) {
    return knowledgeRetrievalService.retrieve(new RetrievalRequest(
        query, knowledgeBaseIds, DEFAULT_TOP_K, DEFAULT_MIN_SCORE));
  }

  private static List<Message> toMessages(List<KnowledgeBaseQueryHistoryMessage> history) {
    if (history == null || history.isEmpty()) {
      return List.of();
    }
    return history.stream()
        .filter(message -> message != null && message.content() != null
            && !message.content().isBlank())
        .filter(message -> "user".equalsIgnoreCase(message.role())
            || "assistant".equalsIgnoreCase(message.role()))
        .map(message -> "assistant".equalsIgnoreCase(message.role())
            ? new AssistantMessage(message.content())
            : new UserMessage(message.content()))
        .map(message -> (Message) message)
        .toList();
  }

  private List<Long> validateAndNormalize(KnowledgeBaseQueryRequest request) {
    if (request == null || request.question() == null || request.question().isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Question must not be blank");
    }
    if (request.knowledgeBaseIds() == null || request.knowledgeBaseIds().isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Knowledge base ids must not be empty");
    }
    if (request.knowledgeBaseIds().stream().anyMatch(id -> id == null || id <= 0)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Knowledge base ids must be positive");
    }
    return List.copyOf(new LinkedHashSet<>(request.knowledgeBaseIds()));
  }

  private void validateKnowledgeBases(List<Long> knowledgeBaseIds) {
    List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findAllById(knowledgeBaseIds);
    Set<Long> foundIds = knowledgeBases.stream()
        .map(KnowledgeBaseEntity::getId)
        .collect(Collectors.toSet());
    List<Long> missingIds = knowledgeBaseIds.stream()
        .filter(id -> !foundIds.contains(id))
        .toList();
    if (!missingIds.isEmpty()) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_NOT_FOUND,
          "Knowledge bases not found: " + missingIds);
    }

    List<Long> unavailableIds = knowledgeBases.stream()
        .filter(kb -> !Boolean.TRUE.equals(kb.getEnabled())
            || kb.getVectorStatus() != VectorStatus.COMPLETED)
        .map(KnowledgeBaseEntity::getId)
        .toList();
    if (!unavailableIds.isEmpty()) {
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "Knowledge bases are not available for query: " + unavailableIds);
    }
  }

  private String buildUserPrompt(String question, List<RetrievedChunkDTO> chunks) {
    StringBuilder context = new StringBuilder();
    for (int index = 0; index < chunks.size(); index++) {
      RetrievedChunkDTO chunk = chunks.get(index);
      context.append("[片段 ").append(index + 1)
          .append(" | 来源: ").append(promptSanitizer.sanitize(chunk.sourceName()))
          .append(" | 类型: ").append(promptSanitizer.sanitize(chunk.sourceType()))
          .append(" | 分类: ").append(promptSanitizer.sanitize(chunk.category()))
          .append("]\n")
          .append(promptSanitizer.sanitize(chunk.content()))
          .append("\n\n");
    }
    return userPromptTemplate.render(Map.of(
        "context", context.toString().strip(),
        "question", question));
  }

  private List<KnowledgeBaseQuerySourceDTO> toSources(List<RetrievedChunkDTO> chunks) {
    return chunks.stream()
        .map(chunk -> new KnowledgeBaseQuerySourceDTO(
            chunk.sourceName(),
            chunk.sourceType(),
            chunk.category(),
            chunk.chunkIndex(),
            chunk.sourceKey(),
            chunk.knowledgeBaseId(),
            chunk.version(),
            chunk.score(),
            chunk.chunkId()))
        .toList();
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR,
          "Failed to serialize RAG sources",
          ex);
    }
  }

  private static ServerSentEvent<String> event(String name, String data) {
    return ServerSentEvent.<String>builder()
        .event(name)
        .data(data)
        .build();
  }

  private record PreparedQuery(
      List<RetrievedChunkDTO> chunks,
      String systemPrompt,
      String userPrompt
  ) {}
}
