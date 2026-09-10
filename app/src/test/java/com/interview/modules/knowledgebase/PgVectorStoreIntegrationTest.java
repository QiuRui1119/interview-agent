package com.interview.modules.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.interview.common.config.LlmEmbeddingConfig;
import com.interview.common.config.LlmProviderProperties;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 使用开发 PostgreSQL/pgvector 和真实 embedding API 验证 VectorStore Bean。
 *
 * <p>默认跳过。运行前设置 RUN_PGVECTOR_INTEGRATION_TESTS=true，
 * 并提供 PostgreSQL 与 PGVECTOR_TEST_EMBEDDING_API_KEY。</p>
 */
@SpringJUnitConfig(PgVectorStoreIntegrationTest.TestConfig.class)
@EnabledIfEnvironmentVariable(named = "RUN_PGVECTOR_INTEGRATION_TESTS", matches = "true")
class PgVectorStoreIntegrationTest {

  private static final String TEST_PROVIDER = "test-embedding";

  @Autowired
  private VectorStore vectorStore;

  private List<Document> testDocuments;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> envOrDefault(
        "PGVECTOR_TEST_JDBC_URL", "jdbc:postgresql://localhost:5432/interview_agent"));
    registry.add("spring.datasource.username", () -> envOrDefault("PGVECTOR_TEST_DB_USER", "postgres"));
    registry.add("spring.datasource.password", () -> envOrDefault("PGVECTOR_TEST_DB_PASSWORD", "123456"));
    registry.add("app.ai.default-embedding-provider", () -> TEST_PROVIDER);
    registry.add("app.ai.providers." + TEST_PROVIDER + ".base-url",
        () -> envOrDefault("PGVECTOR_TEST_EMBEDDING_BASE_URL",
            "https://dashscope.aliyuncs.com/compatible-mode/v1"));
    registry.add("app.ai.providers." + TEST_PROVIDER + ".api-key",
        () -> requiredEnv("PGVECTOR_TEST_EMBEDDING_API_KEY"));
    registry.add("app.ai.providers." + TEST_PROVIDER + ".model", () -> "test-chat-model");
    registry.add("app.ai.providers." + TEST_PROVIDER + ".embedding-model",
        () -> envOrDefault("PGVECTOR_TEST_EMBEDDING_MODEL", "text-embedding-v3"));
    registry.add("app.ai.providers." + TEST_PROVIDER + ".embedding-dimensions", () -> "1024");
    registry.add("app.ai.providers." + TEST_PROVIDER + ".supports-embedding", () -> "true");
    registry.add("spring.ai.vectorstore.pgvector.dimensions", () -> "1024");
    registry.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "false");
    registry.add("spring.ai.vectorstore.pgvector.remove-existing-vector-store-table", () -> "false");
  }

  @Test
  void vectorStoreRanksRedisStreamDocumentFirst() {
    String runId = UUID.randomUUID().toString();
    testDocuments = List.of(
        new Document("Redis Stream 使用消费者组处理消息", Map.of("testRunId", runId)),
        new Document("MySQL B+Tree 适合范围查询", Map.of("testRunId", runId)),
        new Document("JVM 垃圾回收包括标记和回收阶段", Map.of("testRunId", runId))
    );

    vectorStore.add(testDocuments);

    List<Document> result = vectorStore.similaritySearch(
        SearchRequest.builder()
            .query("Redis 消息队列如何消费")
            .topK(2)
            .similarityThreshold(0.2)
            .build());

    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getText()).isEqualTo("Redis Stream 使用消费者组处理消息");
  }

  @AfterEach
  void cleanUp() {
    if (testDocuments != null) {
      vectorStore.delete(testDocuments.stream().map(Document::getId).toList());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(LlmProviderProperties.class)
  @Import({LlmProviderRegistry.class, LlmEmbeddingConfig.class})
  @ImportAutoConfiguration({
      DataSourceAutoConfiguration.class,
      JdbcTemplateAutoConfiguration.class,
      PgVectorStoreAutoConfiguration.class
  })
  static class TestConfig {
  }

  private static String envOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private static String requiredEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set for pgvector integration test");
    }
    return value;
  }
}
