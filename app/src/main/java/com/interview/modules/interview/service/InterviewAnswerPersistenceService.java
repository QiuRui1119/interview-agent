package com.interview.modules.interview.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewSessionStatus;
import com.interview.modules.interview.model.InterviewTurnEntity;
import com.interview.modules.interview.model.InterviewTurnStatus;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.repository.InterviewTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import com.interview.modules.interview.model.InterviewSessionCacheRefreshEvent;

@Service
@RequiredArgsConstructor
public class InterviewAnswerPersistenceService {
  private final InterviewSessionRepository sessionRepository;
  private final InterviewTurnRepository turnRepository;
  private final ApplicationEventPublisher eventPublisher;

  public Optional<InterviewTurnEntity> findByRequestId(String sessionId, String requestId) {
    return requestId == null || requestId.isBlank()
        ? Optional.empty()
        : turnRepository.findBySession_SessionIdAndRequestId(sessionId, requestId);
  }

  @Transactional
  public InterviewTurnEntity saveAnswer(String sessionId, Long turnId,
      String requestId, String answer) {
    if (requestId == null || requestId.isBlank() || answer == null || answer.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "requestId and answer are required");
    }
    var duplicate = turnRepository.findBySession_SessionIdAndRequestId(sessionId, requestId);
    if (duplicate.isPresent()) return duplicate.get();
    InterviewSessionEntity session = sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    if (session.getStatus() == InterviewSessionStatus.READY) session.beginProgress();
    if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS
        || !turnId.equals(session.getCurrentTurnId())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Turn is not the current active turn");
    }
    InterviewTurnEntity turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND));
    if (turn.getStatus() != InterviewTurnStatus.ANSWER_PENDING) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Turn is not waiting for an answer");
    }
    turn.recordAnswer(answer, requestId);
    sessionRepository.save(session);
    InterviewTurnEntity saved = turnRepository.saveAndFlush(turn);
    eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(sessionId));
    return saved;
  }

  @Transactional
  public InterviewTurnEntity markDeciding(String sessionId, Long turnId, String requestId) {
    InterviewTurnEntity turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND));
    if (!sessionId.equals(turn.getSession().getSessionId())
        || !requestId.equals(turn.getRequestId())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Answer request does not match turn");
    }
    if (turn.getStatus() == InterviewTurnStatus.ANSWER_PENDING) {
      turn.startDeciding();
      InterviewTurnEntity saved = turnRepository.saveAndFlush(turn);
      eventPublisher.publishEvent(new InterviewSessionCacheRefreshEvent(sessionId));
      return saved;
    }
    return turn;
  }
}
