package com.interview.modules.interview.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.modules.interview.listener.InterviewGenerationStreamProducer;
import com.interview.modules.interview.model.CreateInterviewSessionRequest;
import com.interview.modules.interview.model.CreateInterviewSessionResponse;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.interview.modules.knowledgebase.model.VectorStatus;
import com.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.interview.modules.resume.model.ResumeEntity;
import com.interview.modules.resume.repository.ResumeRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewSessionCreateService {
  private final ResumeRepository resumeRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final InterviewSessionPersistenceService persistenceService;
  private final InterviewGenerationStreamProducer producer;

  public CreateInterviewSessionResponse create(CreateInterviewSessionRequest request) {
    if (request == null || request.resumeId() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "resumeId is required");
    }
    ResumeEntity resume = resumeRepository.findById(request.resumeId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
    if (resume.getAnalyzeStatus() != AsyncTaskStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Resume analysis is not completed");
    }
    Set<Long> kbIds = new LinkedHashSet<>(request.knowledgeBaseIds() == null
        ? List.of() : request.knowledgeBaseIds());
    if (kbIds.isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "At least one knowledge base is required");
    }
    List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findAllById(kbIds);
    if (knowledgeBases.size() != kbIds.size() || knowledgeBases.stream().anyMatch(kb ->
        kb.getVectorStatus() != VectorStatus.COMPLETED || !Boolean.TRUE.equals(kb.getEnabled()))) {
      throw new BusinessException(ErrorCode.BAD_REQUEST,
          "All selected knowledge bases must exist, be enabled and completed");
    }
    InterviewSessionEntity session = persistenceService.create(
        resume.getId(), value(request.skillId(), "java-backend"), value(request.difficulty(), "mid"),
        5, 1, 10, kbIds);
    producer.publish(session.getSessionId());
    return new CreateInterviewSessionResponse(session.getSessionId(), session.getStatus());
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.strip();
  }
}
