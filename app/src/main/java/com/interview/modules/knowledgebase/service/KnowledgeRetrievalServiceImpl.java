package com.interview.modules.knowledgebase.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.file.TextCleaningService;
import com.interview.modules.knowledgebase.model.RetrievedChunkDTO;
import com.interview.modules.knowledgebase.model.RetrievalRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

  private final TextCleaningService textCleaningService;
  private final VectorStore vectorStore;

  @Override
  public List<RetrievedChunkDTO> retrieve(RetrievalRequest request) {
    validateRequest(request);
    String cleanedQuery = textCleaningService.cleanText(request.query());
    if (cleanedQuery.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Retrieval query must not be blank");
    }

    SearchRequest.Builder searchRequest = SearchRequest.builder()
        .query(cleanedQuery)
        .topK(request.topK())
        .similarityThreshold(request.minScore());
    searchRequest.filterExpression(buildFilter(request.knowledgeBaseIds()));

    List<Document> documents = vectorStore.similaritySearch(searchRequest.build());
    Map<String, RetrievedChunkDTO> uniqueChunks = new LinkedHashMap<>();
    for (Document document : documents) {
      String content = Objects.requireNonNullElse(document.getText(), "");
      String chunkId = document.getId();
      String deduplicationKey = chunkId == null || chunkId.isBlank() ? content : chunkId;
      uniqueChunks.putIfAbsent(deduplicationKey, toDTO(document));
      if (uniqueChunks.size() >= request.topK()) {
        break;
      }
    }
    return new ArrayList<>(uniqueChunks.values());
  }

  private org.springframework.ai.vectorstore.filter.Filter.Expression buildFilter(
      List<Long> knowledgeBaseIds) {
    FilterExpressionBuilder builder = new FilterExpressionBuilder();
    var currentVectors = builder.eq("is_current", true);
    if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
      return currentVectors.build();
    }
    List<Object> ids = knowledgeBaseIds.stream().map(String::valueOf).map(value -> (Object) value).toList();
    return builder.and(builder.in("kb_id", ids), currentVectors).build();
  }

  private RetrievedChunkDTO toDTO(Document document) {
    Map<String, Object> metadata = document.getMetadata();
    return new RetrievedChunkDTO(
        document.getText(),
        metadataValue(metadata, "source_name"),
        metadataValue(metadata, "source_key"),
        metadataValue(metadata, "source_type"),
        metadataValue(metadata, "category"),
        document.getId(),
        document.getScore());
  }

  private static String metadataValue(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    return value == null ? null : value.toString();
  }

  private static void validateRequest(RetrievalRequest request) {
    if (request == null || request.query() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Retrieval request and query are required");
    }
    if (request.topK() <= 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Retrieval topK must be positive");
    }
    if (request.minScore() < 0 || request.minScore() > 1) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Retrieval minScore must be in [0,1]");
    }
  }
}
