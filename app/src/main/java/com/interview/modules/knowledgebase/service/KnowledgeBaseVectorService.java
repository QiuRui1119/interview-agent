package com.interview.modules.knowledgebase.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.interview.modules.knowledgebase.repository.VectorRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseVectorService {

  private final VectorRepository vectorRepository;
  private final TokenTextSplitter textSplitter = TokenTextSplitter.builder().build();

  public int vectorize(KnowledgeBaseEntity knowledgeBase, String content, String jobId) {
    if (knowledgeBase == null || knowledgeBase.getId() == null) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
          "Knowledge base must contain an id");
    }
    if (content == null || content.isBlank()) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED,
          "Knowledge base content must not be blank: " + knowledgeBase.getId());
    }
    if (jobId == null || jobId.isBlank()) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
          "Knowledge base vector job id must not be blank");
    }

    Document sourceDocument = Document.builder()
        .text(content)
        .metadata(Map.of())
        .build();
    List<Document> splitDocuments = textSplitter.apply(List.of(sourceDocument));
    if (splitDocuments.isEmpty()) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED,
          "Knowledge base document produced no vector chunks: " + knowledgeBase.getId());
    }

    List<Document> documents = new ArrayList<>(splitDocuments.size());
    for (int index = 0; index < splitDocuments.size(); index++) {
      documents.add(withMetadata(splitDocuments.get(index), knowledgeBase, index, jobId));
    }

    vectorRepository.saveAll(documents);
    return documents.size();
  }

  private Document withMetadata(
      Document chunk,
      KnowledgeBaseEntity knowledgeBase,
      int index,
      String jobId
  ) {
    String safeSourceKey = knowledgeBase.getSourceKey() == null
        ? ""
        : knowledgeBase.getSourceKey();

    Map<String, Object> metadata = Map.of(
        "kb_id", knowledgeBase.getId().toString(),
        "source_type", knowledgeBase.getSourceType().name(),
        "source_name", knowledgeBase.getName(),
        "source_key", safeSourceKey,
        "category", knowledgeBase.getCategory(),
        "version", knowledgeBase.getVersion().toString(),
        "chunk_index", index,
        "kb_vector_job_id", jobId,
        "is_current", false);

    return Document.builder()
        .text(chunk.getText())
        .metadata(metadata)
        .build();
  }
}
