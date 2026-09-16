package com.interview.modules.knowledgebase.service;

import com.interview.common.config.RagProperties;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import com.interview.infrastructure.ai.PromptSanitizer;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class QueryRewriteService {

  private static final Logger log = LoggerFactory.getLogger(QueryRewriteService.class);
  private static final int SHORT_QUESTION_CHARS = 40;
  private static final Pattern REFERENCE_PATTERN = Pattern.compile(
      "它|这个|那个|上述|前面|刚才|之后|为什么|\\b(it|this|that|above|afterwards|why)\\b",
      Pattern.CASE_INSENSITIVE);

  private final LlmProviderRegistry llmProviderRegistry;
  private final PromptSanitizer promptSanitizer;
  private final RagProperties.Rewrite properties;
  private final String systemPrompt;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final LongAdder llmCalls = new LongAdder();
  private final LongAdder totalLatencyMillis = new LongAdder();
  private final LongAdder failures = new LongAdder();

  public QueryRewriteService(
      LlmProviderRegistry llmProviderRegistry,
      PromptSanitizer promptSanitizer,
      RagProperties ragProperties,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.promptSanitizer = promptSanitizer;
    this.properties = ragProperties.rewrite();
    this.systemPrompt = resourceLoader
        .getResource("classpath:prompts/knowledgebase-query-rewrite-system.st")
        .getContentAsString(StandardCharsets.UTF_8);
  }

  public boolean shouldRewrite(String question, List<Message> history, boolean originalMissed) {
    if (!properties.enabled() || question == null || question.isBlank()
        || history == null || history.isEmpty()) {
      return false;
    }
    String normalized = question.strip();
    return originalMissed
        || normalized.length() <= SHORT_QUESTION_CHARS
        || REFERENCE_PATTERN.matcher(normalized).find();
  }

  public String rewrite(String question, List<Message> history) {
    String original = question == null ? "" : question.strip();
    if (!properties.enabled() || original.isBlank() || history == null || history.isEmpty()) {
      return original;
    }

    String prompt = buildPrompt(original, history);
    long startedAt = System.nanoTime();
    llmCalls.increment();
    Future<String> future = executor.submit(() -> llmProviderRegistry.getDefaultChatClient()
        .prompt()
        .system(systemPrompt)
        .user(prompt)
        .call()
        .content());
    try {
      String rewritten = future.get(properties.timeoutSeconds(), TimeUnit.SECONDS);
      String result = normalizeResult(rewritten, original);
      totalLatencyMillis.add(elapsedMillis(startedAt));
      log.info("RAG query rewrite completed: changed={}, durationMs={}",
          !result.equals(original), elapsedMillis(startedAt));
      return result;
    } catch (TimeoutException ex) {
      totalLatencyMillis.add(elapsedMillis(startedAt));
      failures.increment();
      future.cancel(true);
      log.warn("RAG query rewrite timed out after {} seconds", properties.timeoutSeconds());
      return original;
    } catch (InterruptedException ex) {
      totalLatencyMillis.add(elapsedMillis(startedAt));
      failures.increment();
      future.cancel(true);
      Thread.currentThread().interrupt();
      log.warn("RAG query rewrite interrupted");
      return original;
    } catch (Exception ex) {
      totalLatencyMillis.add(elapsedMillis(startedAt));
      failures.increment();
      log.warn("RAG query rewrite failed, using original question: {}", ex.getMessage());
      return original;
    }
  }

  public RewriteStats stats() {
    long calls = llmCalls.sum();
    return new RewriteStats(
        calls,
        failures.sum(),
        calls == 0 ? 0 : (double) totalLatencyMillis.sum() / calls);
  }

  public record RewriteStats(long llmCalls, long failures, double averageLatencyMillis) {}

  private String buildPrompt(String question, List<Message> history) {
    StringBuilder historyText = new StringBuilder();
    for (Message message : history) {
      String content = promptSanitizer.sanitize(message.getText());
      if (!content.isBlank()) {
        historyText.append(message.getMessageType().getValue())
            .append(": ").append(content).append('\n');
      }
    }
    String boundedHistory = tail(historyText.toString(), properties.maxHistoryChars());
    String boundedQuestion = promptSanitizer.sanitize(question);
    if (boundedQuestion.length() > properties.maxQuestionChars()) {
      boundedQuestion = boundedQuestion.substring(0, properties.maxQuestionChars());
    }
    return "Conversation history:\n" + boundedHistory
        + "\nCurrent question:\n" + boundedQuestion;
  }

  private String normalizeResult(String rewritten, String original) {
    if (rewritten == null || rewritten.isBlank()) {
      return original;
    }
    String sanitized = promptSanitizer.sanitize(rewritten).strip();
    if (sanitized.length() > properties.maxQuestionChars()) {
      sanitized = sanitized.substring(0, properties.maxQuestionChars()).strip();
    }
    return sanitized.isBlank() ? original : sanitized;
  }

  private static String tail(String value, int maxChars) {
    return value.length() <= maxChars ? value : value.substring(value.length() - maxChars);
  }

  private static long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  @PreDestroy
  void closeExecutor() {
    executor.shutdownNow();
  }
}
