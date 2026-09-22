package com.interview.modules.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import com.interview.infrastructure.ai.StructuredOutputInvoker;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewSessionStatus;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.knowledgebase.model.RetrievalRequest;
import com.interview.modules.knowledgebase.model.RetrievedChunkDTO;
import com.interview.modules.knowledgebase.service.KnowledgeRetrievalService;
import com.interview.modules.resume.model.ResumeAnalysisEntity;
import com.interview.modules.resume.model.ResumeEntity;
import com.interview.modules.resume.repository.ResumeAnalysisRepository;
import com.interview.modules.resume.repository.ResumeRepository;
import java.util.List;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ResourceLoader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

@Service
public class InterviewTopicGenerationService {
  private final InterviewSessionRepository sessionRepository;
  private final ResumeRepository resumeRepository;
  private final ResumeAnalysisRepository analysisRepository;
  private final KnowledgeRetrievalService retrievalService;
  private final LlmProviderRegistry llmRegistry;
  private final StructuredOutputInvoker invoker;
  private final InterviewGenerationPersistenceService persistenceService;
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;

  public InterviewTopicGenerationService(InterviewSessionRepository sessionRepository,
      ResumeRepository resumeRepository, ResumeAnalysisRepository analysisRepository,
      KnowledgeRetrievalService retrievalService, LlmProviderRegistry llmRegistry,
      StructuredOutputInvoker invoker, InterviewGenerationPersistenceService persistenceService,
      ObjectMapper objectMapper, ResourceLoader resourceLoader) {
    this.sessionRepository = sessionRepository;
    this.resumeRepository = resumeRepository;
    this.analysisRepository = analysisRepository;
    this.retrievalService = retrievalService;
    this.llmRegistry = llmRegistry;
    this.invoker = invoker;
    this.persistenceService = persistenceService;
    this.objectMapper = objectMapper;
    this.resourceLoader = resourceLoader;
  }

  public boolean generate(String sessionId) {
    InterviewSessionEntity session = find(sessionId);
    if (session.getStatus() == InterviewSessionStatus.READY) return false;
    if (session.getStatus() != InterviewSessionStatus.GENERATING) {
      throw new BusinessException(ErrorCode.INVALID_INTERVIEW_STATE_TRANSITION,
          "Session is not generating: " + session.getStatus());
    }
    markProcessing(session);
    ResumeEntity resume = resumeRepository.findById(session.getResumeId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
    ResumeAnalysisEntity analysis = analysisRepository
        .findByResumeEntityOrderByAnalysisTimeDesc(resume).stream().findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_ANALYSIS_NOT_FOUND));

    List<TopicPlan> plans = planTopics(session, resume, analysis);
    List<GeneratedTopic> generated = plans.stream().limit(5)
        .map(plan -> generateTopic(session, plan)).toList();
    if (generated.size() != 5) {
      throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
          "Topic planner must return exactly 5 topics");
    }
    persistenceService.save(sessionId, generated);
    return true;
  }

  private List<TopicPlan> planTopics(InterviewSessionEntity session, ResumeEntity resume,
      ResumeAnalysisEntity analysis) {
    BeanOutputConverter<TopicPlanResponse> converter = new BeanOutputConverter<>(TopicPlanResponse.class);
    String prompt = "Generate exactly 5 interview topics as JSON. skill=" + session.getSkillId()
        + ", difficulty=" + session.getDifficulty() + "\nResume:\n" + resume.getResumeText()
        + "\nAnalysis:\n" + analysis.getSummary() + "\nSkill instructions:\n"
        + loadSkill(session.getSkillId());
    return invoker.invoke(llmRegistry.getDefaultChatClient(),
        "You plan focused technical interviews. Return diverse topics.\n" + converter.getFormat(),
        prompt, converter).topics();
  }

  private GeneratedTopic generateTopic(InterviewSessionEntity session, TopicPlan plan) {
    List<RetrievedChunkDTO> evidence = retrievalService.retrieve(new RetrievalRequest(
        plan.retrievalQuery(), List.copyOf(session.getKnowledgeBaseIds()), 4, 0.3));
    BeanOutputConverter<QuestionOutput> converter = new BeanOutputConverter<>(QuestionOutput.class);
    String context = evidence.stream().map(RetrievedChunkDTO::content)
        .reduce("", (left, right) -> left + "\n---\n" + right);
    QuestionOutput output = invoker.invoke(llmRegistry.getDefaultChatClient(),
        "Generate one main question, one fallback follow-up, a reference answer and scoring points. "
            + "Use only the supplied evidence.\n" + converter.getFormat(),
        "Topic: " + plan.topicName() + "\nObjective: " + plan.objective()
            + "\nEvidence:\n" + context, converter);
    return new GeneratedTopic(plan.category(), plan.topicName(), plan.objective(),
        plan.retrievalQuery(), output.baseQuestion(), output.fallbackFollowUp(),
        output.referenceAnswer(), json(output.scoringPoints()), evidence);
  }

  @Transactional
  public void markFailed(String sessionId, String error) {
    sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
      if (session.getStatus() == InterviewSessionStatus.GENERATING) {
        session.markGenerationFailed(error);
        sessionRepository.save(session);
      }
    });
  }

  @Transactional
  protected void markProcessing(InterviewSessionEntity session) {
    session.markGenerationProcessing();
    sessionRepository.save(session);
  }

  private InterviewSessionEntity find(String sessionId) {
    return sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
  }

  private String json(List<String> value) {
    try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
    catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
  }

  private String loadSkill(String skillId) {
    String normalized = skillId == null || skillId.isBlank() ? "java-backend" : skillId;
    try {
      return resourceLoader.getResource("classpath:skills/" + normalized + "/SKILL.md")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
          "Skill definition not found: " + normalized, ex);
    }
  }

  private record TopicPlanResponse(List<TopicPlan> topics) {}
  private record TopicPlan(String category, String topicName, String objective,
                           String retrievalQuery) {}
  private record QuestionOutput(String baseQuestion, String fallbackFollowUp,
                                String referenceAnswer, List<String> scoringPoints) {}
  public record GeneratedTopic(String category, String topicName, String objective,
      String retrievalQuery, String baseQuestion, String fallbackFollowUp,
      String referenceAnswer, String scoringPointsJson, List<RetrievedChunkDTO> evidence) {}
}
