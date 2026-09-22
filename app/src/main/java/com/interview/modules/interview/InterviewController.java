package com.interview.modules.interview;

import com.interview.modules.interview.model.CreateInterviewSessionRequest;
import com.interview.modules.interview.model.CreateInterviewSessionResponse;
import com.interview.modules.interview.model.InterviewAnswerRequest;
import com.interview.modules.interview.model.InterviewAnswerResponse;
import com.interview.modules.interview.service.InterviewAnswerDecisionService;
import com.interview.modules.interview.service.InterviewAnswerPersistenceService;
import com.interview.modules.interview.service.InterviewSessionReadService;
import com.interview.modules.interview.service.InterviewSessionResumeService;
import com.interview.modules.interview.model.InterviewResumeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import com.interview.modules.interview.service.InterviewSessionCreateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewController {
  private final InterviewSessionCreateService createService;
  private final InterviewAnswerPersistenceService answerPersistenceService;
  private final InterviewAnswerDecisionService decisionService;
  private final InterviewSessionReadService readService;
  private final InterviewSessionResumeService resumeService;

  @PostMapping("/api/interview/sessions")
  public ResponseEntity<CreateInterviewSessionResponse> create(
      @RequestBody CreateInterviewSessionRequest request) {
    return ResponseEntity.accepted().body(createService.create(request));
  }

  @PostMapping("/api/interview/sessions/{sessionId}/turns/{turnId}/answer")
  public ResponseEntity<InterviewAnswerResponse> answer(
      @PathVariable String sessionId,
      @PathVariable Long turnId,
      @RequestBody InterviewAnswerRequest request) {
    var existing = answerPersistenceService.findByRequestId(sessionId, request.requestId());
    if (existing.isPresent()) {
      return ResponseEntity.ok(decisionService.responseForExisting(sessionId, existing.get()));
    }
    var savedTurn = answerPersistenceService.saveAnswer(
        sessionId, turnId, request.requestId(), request.answer());
    if (savedTurn.getStatus() != com.interview.modules.interview.model.InterviewTurnStatus.ANSWER_PENDING) {
      return ResponseEntity.ok(decisionService.responseForExisting(sessionId, savedTurn));
    }
    answerPersistenceService.markDeciding(sessionId, turnId, request.requestId());
    InterviewAnswerResponse response;
    try {
      var decision = decisionService.decide(sessionId, turnId);
      response = decisionService.persist(sessionId, turnId, request.requestId(), decision);
    } catch (com.interview.common.exception.BusinessException ex) {
      if (!ex.getCode().equals(com.interview.common.exception.ErrorCode.AI_SERVICE_ERROR.getCode())
          && !ex.getCode().equals(com.interview.common.exception.ErrorCode.AI_SERVICE_TIMEOUT.getCode())) {
        throw ex;
      }
      var fallback = decisionService.fallbackDecision(sessionId, turnId);
      if (fallback == null) {
        var pending = answerPersistenceService.findByRequestId(sessionId, request.requestId())
            .orElseThrow(() -> ex);
        response = decisionService.responseForExisting(sessionId, pending);
      } else {
        response = decisionService.persist(sessionId, turnId, request.requestId(), fallback);
      }
    }
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/interview/sessions/{sessionId}/resume")
  public ResponseEntity<InterviewResumeResponse> resume(@PathVariable String sessionId) {
    return ResponseEntity.ok(resumeService.resume(sessionId));
  }
}
