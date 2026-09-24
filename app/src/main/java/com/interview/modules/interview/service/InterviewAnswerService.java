package com.interview.modules.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.ai.LlmProviderRegistry;
import com.interview.infrastructure.ai.StructuredOutputInvoker;
import com.interview.modules.interview.model.InterviewAnswerRequest;
import com.interview.modules.interview.model.InterviewAnswerResponse;
import com.interview.modules.interview.model.InterviewEvaluationRequestedEvent;
import com.interview.modules.interview.model.InterviewSessionCacheRefreshEvent;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAnswerService {
  private final InterviewSessionRepository sessionRepository;
  private final InterviewTopicRepository topicRepository;
  private final InterviewTurnRepository turnRepository;
  private final LlmProviderRegistry llmRegistry;
  private final StructuredOutputInvoker invoker;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;

  public InterviewAnswerResponse submitAnswer(String sessionId, Long turnId,
      InterviewAnswerRequest request) {
    validateRequest(request);
    InterviewTurnEntity existing = transactionTemplate.execute(status -> turnRepository
        .findBySession_SessionIdAndRequestId(sessionId, request.requestId()).orElse(null));
    if (existing != null) return responseForExisting(sessionId, existing.getId());

    InterviewTurnEntity saved = saveAnswer(sessionId, turnId, request);
    if (saved.getStatus() != InterviewTurnStatus.ANSWER_PENDING) {
      return responseForExisting(sessionId, saved.getId());
    }
    markDeciding(sessionId, turnId, request.requestId());
    return decideWithFallback(sessionId, turnId, request.requestId());
  }

  public void recoverDecision(String sessionId, Long turnId, String requestId) {
    decideWithFallback(sessionId, turnId, requestId);
  }

  public String findRequestId(Long turnId) {
    return turnRepository.findById(turnId).map(InterviewTurnEntity::getRequestId).orElse(null);
  }

  public boolean hasTurnAfter(String sessionId, Integer turnOrder) {
    return turnRepository.existsBySession_SessionIdAndTurnOrderGreaterThan(sessionId, turnOrder);
  }

  private InterviewAnswerResponse decideWithFallback(String sessionId, Long turnId,
      String requestId) {
    try {
      return persist(sessionId, turnId, requestId, decide(sessionId, turnId));
    } catch (BusinessException ex) {
      if (!isAiFailure(ex)) throw ex;
      DecisionResult fallback = fallbackDecision(sessionId, turnId);
      return fallback == null
          ? responseForExisting(sessionId, turnId)
          : persist(sessionId, turnId, requestId, fallback);
    }
  }

  private InterviewTurnEntity saveAnswer(String sessionId, Long turnId,
      InterviewAnswerRequest request) {
    return transactionTemplate.execute(status -> {
      InterviewTurnEntity duplicate = turnRepository
          .findBySession_SessionIdAndRequestId(sessionId, request.requestId()).orElse(null);
      if (duplicate != null) return duplicate;
      InterviewSessionEntity session = findSession(sessionId);
      if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS
          || !turnId.equals(session.getCurrentTurnId())) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Turn is not the current active turn");
      }
      InterviewTurnEntity turn = findTurn(turnId);
      if (turn.getStatus() != InterviewTurnStatus.ANSWER_PENDING) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Turn is not waiting for an answer");
      }
      turn.recordAnswer(request.answer(), request.requestId());
      InterviewTurnEntity result = turnRepository.saveAndFlush(turn);
      eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(sessionId));
      return result;
    });
  }

  private void markDeciding(String sessionId, Long turnId, String requestId) {
    transactionTemplate.executeWithoutResult(status -> {
      InterviewTurnEntity turn = findTurn(turnId);
      if (!sessionId.equals(turn.getSession().getSessionId())
          || !requestId.equals(turn.getRequestId())) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Answer request does not match turn");
      }
      if (turn.getStatus() == InterviewTurnStatus.ANSWER_PENDING) {
        turn.startDeciding();
        turnRepository.saveAndFlush(turn);
        eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(sessionId));
      }
    });
  }

  private DecisionResult decide(String sessionId, Long turnId) {
    DecisionContext context = transactionTemplate.execute(status -> decisionContext(sessionId, turnId));
    BeanOutputConverter<DecisionOutput> converter = new BeanOutputConverter<>(DecisionOutput.class);
    DecisionOutput output = invoker.invoke(llmRegistry.getDefaultChatClient(),
        "Evaluate the answer. action must be FOLLOW_UP, NEXT_TOPIC, or END. "
            + "Return JSON only.\n" + converter.getFormat(),
        "Question: " + context.question() + "\nAnswer: " + context.answer()
            + "\nTopic objective: " + context.objective()
            + "\nScoring points: " + context.scoringPoints()
            + "\nReference answer: " + context.referenceAnswer(), converter);
    return new DecisionResult(output.action(), output.coverageScore(), output.coveredPoints(),
        output.missingPoints(), output.nextQuestion());
  }

  private DecisionResult fallbackDecision(String sessionId, Long turnId) {
    return transactionTemplate.execute(status -> {
      InterviewTurnEntity turn = findTurn(turnId);
      if (!sessionId.equals(turn.getSession().getSessionId())
          || turn.getStatus() != InterviewTurnStatus.DECIDING) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Turn is not ready for fallback decision");
      }
      String fallback = turn.getTopic().getFallbackFollowUp();
      return fallback == null || fallback.isBlank() ? null
          : new DecisionResult("FOLLOW_UP", 0, List.of(), List.of(), fallback);
    });
  }

  private InterviewAnswerResponse persist(String sessionId, Long turnId, String requestId,
      DecisionResult decision) {
    return transactionTemplate.execute(status -> {
      InterviewSessionEntity session = findSession(sessionId);
      InterviewTurnEntity current = findTurn(turnId);
      if (current.getStatus() != InterviewTurnStatus.DECIDING) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Answer decision was already persisted");
      }
      String action = normalizeAction(decision.action());
      InterviewTopicEntity topic = current.getTopic();
      if ("FOLLOW_UP".equals(action) && topic.getFollowUpCount() >= topic.getMaxFollowUps()) {
        action = "NEXT_TOPIC";
      }
      current.recordDecision(toJson(new DecisionResult(action, decision.coverageScore(),
          decision.coveredPoints(), decision.missingPoints(), decision.nextQuestion())));
      if (topic.getStatus() == InterviewTopicStatus.PENDING) {
        topic.transitionTo(InterviewTopicStatus.ACTIVE);
      }
      Long nextTurnId = switch (action) {
        case "FOLLOW_UP" -> createFollowUp(session, topic, current, decision.nextQuestion());
        case "NEXT_TOPIC" -> moveToNextTopic(sessionId, session, topic);
        default -> {
          topic.complete();
          session.complete();
          yield null;
        }
      };
      turnRepository.save(current);
      if (session.getStatus() == InterviewSessionStatus.COMPLETED
          && session.getEvaluateStatus() == null) {
        session.markEvaluationPending();
        eventPublisher.publishEvent(new InterviewEvaluationRequestedEvent(sessionId));
      }
      sessionRepository.save(session);
      eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(sessionId));
      log.info("Interview answer decision: sessionId={}, turnId={}, action={}",
          sessionId, turnId, action);
      return new InterviewAnswerResponse(requestId, action, nextTurnId,
          current.getStatus(), session.getStatus());
    });
  }

  private Long createFollowUp(InterviewSessionEntity session, InterviewTopicEntity topic,
      InterviewTurnEntity current, String nextQuestion) {
    topic.incrementFollowUpCount();
    InterviewTurnEntity next = InterviewTurnEntity.createFollowUp(
        topic, session.getTotalTurns(), nextQuestion, current);
    next.transitionTo(InterviewTurnStatus.ANSWER_PENDING);
    Long nextTurnId = turnRepository.save(next).getId();
    session.moveToTurn(nextTurnId, topic.getTopicOrder());
    return nextTurnId;
  }

  private Long moveToNextTopic(String sessionId, InterviewSessionEntity session,
      InterviewTopicEntity topic) {
    topic.complete();
    var nextTopic = topicRepository.findBySession_SessionIdAndTopicOrder(
        sessionId, topic.getTopicOrder() + 1);
    if (nextTopic.isEmpty() || session.getTotalTurns() >= session.getMaxTurns()) {
      session.complete();
      return null;
    }
    nextTopic.get().transitionTo(InterviewTopicStatus.ACTIVE);
    InterviewTurnEntity next = InterviewTurnEntity.create(nextTopic.get(),
        session.getTotalTurns(), nextTopic.get().getBaseQuestion());
    next.transitionTo(InterviewTurnStatus.ANSWER_PENDING);
    Long nextTurnId = turnRepository.save(next).getId();
    session.moveToTurn(nextTurnId, nextTopic.get().getTopicOrder());
    return nextTurnId;
  }

  private InterviewAnswerResponse responseForExisting(String sessionId, Long turnId) {
    return transactionTemplate.execute(status -> {
      InterviewSessionEntity session = findSession(sessionId);
      InterviewTurnEntity turn = findTurn(turnId);
      if (turn.getStatus() != InterviewTurnStatus.DECIDED) {
        return new InterviewAnswerResponse(turn.getRequestId(), "PENDING",
            session.getCurrentTurnId(), turn.getStatus(), session.getStatus());
      }
      String action = extractAction(turn.getDecision());
      Long nextTurnId = session.getCurrentTurnId().equals(turn.getId())
          ? null : session.getCurrentTurnId();
      return new InterviewAnswerResponse(turn.getRequestId(), action, nextTurnId,
          turn.getStatus(), session.getStatus());
    });
  }

  private DecisionContext decisionContext(String sessionId, Long turnId) {
    InterviewTurnEntity turn = findTurn(turnId);
    if (!sessionId.equals(turn.getSession().getSessionId())
        || turn.getStatus() != InterviewTurnStatus.DECIDING) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Turn is not ready for decision");
    }
    InterviewTopicEntity topic = turn.getTopic();
    return new DecisionContext(turn.getQuestion(), turn.getAnswer(), topic.getObjective(),
        topic.getScoringPointsJson(), topic.getReferenceAnswer());
  }

  private InterviewSessionEntity findSession(String sessionId) {
    return sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
  }

  private InterviewTurnEntity findTurn(Long turnId) {
    return turnRepository.findById(turnId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND));
  }

  private static void validateRequest(InterviewAnswerRequest request) {
    if (request == null || request.requestId() == null || request.requestId().isBlank()
        || request.answer() == null || request.answer().isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "requestId and answer are required");
    }
  }

  private static boolean isAiFailure(BusinessException ex) {
    return ex.getCode().equals(ErrorCode.AI_SERVICE_ERROR.getCode())
        || ex.getCode().equals(ErrorCode.AI_SERVICE_TIMEOUT.getCode());
  }

  private static String normalizeAction(String action) {
    if (action == null) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Missing decision action");
    }
    String value = action.strip().toUpperCase();
    if (!List.of("FOLLOW_UP", "NEXT_TOPIC", "END").contains(value)) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Invalid decision action: " + action);
    }
    return value;
  }

  private String extractAction(String decision) {
    try {
      return objectMapper.readTree(decision).path("action").asText("END");
    } catch (Exception ex) {
      return "END";
    }
  }

  private String toJson(DecisionResult result) {
    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to save decision", ex);
    }
  }

  private record DecisionContext(String question, String answer, String objective,
      String scoringPoints, String referenceAnswer) {}
  private record DecisionOutput(String action, int coverageScore, List<String> coveredPoints,
      List<String> missingPoints, String nextQuestion) {}
  public record DecisionResult(String action, int coverageScore, List<String> coveredPoints,
      List<String> missingPoints, String nextQuestion) {}
}
