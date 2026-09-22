package com.interview.modules.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import com.interview.infrastructure.ai.StructuredOutputInvoker;
import com.interview.modules.interview.model.InterviewAnswerResponse;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewSessionStatus;
import com.interview.modules.interview.model.InterviewTopicEntity;
import com.interview.modules.interview.model.InterviewTopicStatus;
import com.interview.modules.interview.model.InterviewTurnEntity;
import com.interview.modules.interview.model.InterviewTurnStatus;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.repository.InterviewTopicRepository;
import com.interview.modules.interview.repository.InterviewTurnRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.interview.modules.interview.model.InterviewSessionCacheRefreshEvent;

@Service
@RequiredArgsConstructor
public class InterviewAnswerDecisionService {
  private final InterviewSessionRepository sessionRepository;
  private final InterviewTopicRepository topicRepository;
  private final InterviewTurnRepository turnRepository;
  private final LlmProviderRegistry llmRegistry;
  private final StructuredOutputInvoker invoker;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public DecisionResult decide(String sessionId, Long turnId) {
    InterviewTurnEntity savedTurn = turnRepository.findById(turnId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND));
    if (!sessionId.equals(savedTurn.getSession().getSessionId())
        || savedTurn.getStatus() != InterviewTurnStatus.DECIDING) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Turn is not ready for decision");
    }
    InterviewTopicEntity topic = savedTurn.getTopic();
    BeanOutputConverter<DecisionOutput> converter = new BeanOutputConverter<>(DecisionOutput.class);
    String prompt = "Question: " + savedTurn.getQuestion() + "\nAnswer: " + savedTurn.getAnswer()
        + "\nTopic objective: " + topic.getObjective()
        + "\nScoring points: " + topic.getScoringPointsJson()
        + "\nReference answer: " + topic.getReferenceAnswer();
    DecisionOutput output = invoker.invoke(llmRegistry.getDefaultChatClient(),
        "Evaluate the answer. action must be FOLLOW_UP, NEXT_TOPIC, or END. "
            + "Return JSON only.\n" + converter.getFormat(), prompt, converter);
    return new DecisionResult(output.action(), output.coverageScore(), output.coveredPoints(),
        output.missingPoints(), output.nextQuestion());
  }

  public DecisionResult fallbackDecision(String sessionId, Long turnId) {
    InterviewTurnEntity turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND));
    if (!sessionId.equals(turn.getSession().getSessionId())
        || turn.getStatus() != InterviewTurnStatus.DECIDING) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Turn is not ready for fallback decision");
    }
    String fallback = turn.getTopic().getFallbackFollowUp();
    if (fallback == null || fallback.isBlank()) {
      return null;
    }
    return new DecisionResult("FOLLOW_UP", 0, List.of(), List.of(), fallback);
  }

  @Transactional
  public InterviewAnswerResponse persist(String sessionId, Long turnId, String requestId,
      DecisionResult decision) {
    InterviewSessionEntity session = sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    InterviewTurnEntity current = turnRepository.findById(turnId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND));
    if (current.getStatus() != InterviewTurnStatus.DECIDING) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Answer decision was already persisted");
    }
    String action = normalizeAction(decision.action());
    current.recordDecision(toJson(decision));
    InterviewTopicEntity topic = current.getTopic();
    Long nextTurnId = null;
    if ("FOLLOW_UP".equals(action) && topic.getFollowUpCount() < topic.getMaxFollowUps()) {
      topic.incrementFollowUpCount();
      InterviewTurnEntity next = InterviewTurnEntity.createFollowUp(
          topic, session.getTotalTurns(), decision.nextQuestion(), current);
      next.transitionTo(InterviewTurnStatus.ANSWER_PENDING);
      nextTurnId = turnRepository.save(next).getId();
      session.moveToTurn(nextTurnId, topic.getTopicOrder());
    } else if ("NEXT_TOPIC".equals(action)) {
      topic.complete();
      var nextTopic = topicRepository.findBySession_SessionIdAndTopicOrder(
          sessionId, topic.getTopicOrder() + 1);
      if (nextTopic.isPresent() && session.getTotalTurns() < session.getMaxTurns()) {
        nextTopic.get().transitionTo(InterviewTopicStatus.ACTIVE);
        InterviewTurnEntity next = InterviewTurnEntity.create(nextTopic.get(),
            session.getTotalTurns(), nextTopic.get().getBaseQuestion());
        next.transitionTo(InterviewTurnStatus.ANSWER_PENDING);
        nextTurnId = turnRepository.save(next).getId();
        session.moveToTurn(nextTurnId, nextTopic.get().getTopicOrder());
      } else {
        session.complete();
      }
    } else {
      topic.complete();
      session.complete();
    }
    turnRepository.save(current);
    sessionRepository.save(session);
    eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(sessionId));
    return new InterviewAnswerResponse(requestId, action, nextTurnId,
        current.getStatus(), session.getStatus());
  }

  @Transactional(readOnly = true)
  public InterviewAnswerResponse responseForExisting(String sessionId, InterviewTurnEntity turn) {
    InterviewSessionEntity session = sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    if (turn.getStatus() != InterviewTurnStatus.DECIDED) {
      return new InterviewAnswerResponse(turn.getRequestId(), "PENDING",
          session.getCurrentTurnId(), turn.getStatus(), session.getStatus());
    }
    String action = extractAction(turn.getDecision());
    Long nextTurnId = session.getCurrentTurnId().equals(turn.getId())
        ? null : session.getCurrentTurnId();
    return new InterviewAnswerResponse(turn.getRequestId(), action, nextTurnId,
        turn.getStatus(), session.getStatus());
  }

  private String extractAction(String decision) {
    try {
      return objectMapper.readTree(decision).path("action").asText("END");
    } catch (Exception ex) {
      return "END";
    }
  }

  private static String normalizeAction(String action) {
    if (action == null) throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Missing decision action");
    String value = action.strip().toUpperCase();
    if (!List.of("FOLLOW_UP", "NEXT_TOPIC", "END").contains(value)) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Invalid decision action: " + action);
    }
    return value;
  }
  private String toJson(DecisionResult result) {
    try { return objectMapper.writeValueAsString(result); }
    catch (JsonProcessingException ex) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to save decision", ex); }
  }
  private record DecisionOutput(String action, int coverageScore, List<String> coveredPoints,
      List<String> missingPoints, String nextQuestion) {}
  public record DecisionResult(String action, int coverageScore, List<String> coveredPoints,
      List<String> missingPoints, String nextQuestion) {}
}
