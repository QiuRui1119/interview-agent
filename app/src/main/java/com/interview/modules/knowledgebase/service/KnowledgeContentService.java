package com.interview.modules.knowledgebase.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.file.DocumentParseService;
import com.interview.infrastructure.file.FileStorageService;
import com.interview.infrastructure.file.TextCleaningService;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeContentService {

  private final FileStorageService fileStorageService;
  private final DocumentParseService documentParseService;
  private final TextCleaningService textCleaningService;

  public String loadContent(KnowledgeBaseEntity knowledgeBase) {
    if (knowledgeBase == null || knowledgeBase.getSourceType() == null) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED,
          "Knowledge base source type must not be null");
    }

    return switch (knowledgeBase.getSourceType()) {
      case USER_UPLOAD -> loadUploadedDocument(knowledgeBase);
      case BUILT_IN -> loadClasspathDocument(knowledgeBase);
    };
  }

  private String loadUploadedDocument(KnowledgeBaseEntity knowledgeBase) {
    String storageKey = requireValue(
        knowledgeBase.getStorageKey(), "Knowledge base storage key must not be blank");
    byte[] fileBytes = fileStorageService.downloadKnowledgeBase(storageKey);
    if (fileBytes == null || fileBytes.length == 0) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED,
          "Knowledge base document is empty: " + knowledgeBase.getId());
    }

    try (InputStream inputStream = new java.io.ByteArrayInputStream(fileBytes)) {
      return clean(documentParseService.parseContent(inputStream), knowledgeBase.getId());
    } catch (IOException | org.apache.tika.exception.TikaException | org.xml.sax.SAXException ex) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED,
          "Failed to parse uploaded knowledge base: " + knowledgeBase.getId(),
          ex);
    }
  }

  private String loadClasspathDocument(KnowledgeBaseEntity knowledgeBase) {
    String sourceKey = requireValue(
        knowledgeBase.getSourceKey(), "Built-in knowledge base source key must not be blank");
    ClassPathResource resource = new ClassPathResource(sourceKey);

    try (InputStream inputStream = resource.getInputStream()) {
      return clean(
          new String(inputStream.readAllBytes(), StandardCharsets.UTF_8),
          knowledgeBase.getId());
    } catch (IOException ex) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED,
          "Failed to load built-in knowledge base: " + sourceKey,
          ex);
    }
  }

  private String clean(String content, Long knowledgeBaseId) {
    String cleaned = textCleaningService.cleanText(content);
    if (cleaned.isBlank()) {
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED,
          "Knowledge base document content is empty: " + knowledgeBaseId);
    }
    return cleaned;
  }

  private static String requireValue(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED, message);
    }
    return value;
  }
}
