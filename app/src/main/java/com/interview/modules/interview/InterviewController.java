package com.interview.modules.interview;

import com.interview.modules.interview.model.CreateInterviewSessionRequest;
import com.interview.modules.interview.model.CreateInterviewSessionResponse;
import com.interview.modules.interview.model.InterviewAnswerRequest;
import com.interview.modules.interview.model.InterviewAnswerResponse;
import com.interview.modules.interview.service.InterviewAnswerService;
import com.interview.modules.interview.service.InterviewSessionService;
import com.interview.modules.interview.service.InterviewEvaluationService;
import com.interview.modules.interview.model.InterviewResumeResponse;
import com.interview.modules.interview.model.InterviewReportStatusResponse;
import com.interview.modules.interview.model.InterviewSessionListItemDTO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewController {
  private final InterviewSessionService sessionService;
  private final InterviewAnswerService answerService;
  private final InterviewEvaluationService evaluationService;

  @PostMapping("/api/interview/sessions")
  public ResponseEntity<CreateInterviewSessionResponse> create(
      @RequestBody CreateInterviewSessionRequest request) {
    return ResponseEntity.accepted().body(sessionService.create(request));
  }

  @GetMapping("/api/interview/sessions")
  public ResponseEntity<List<InterviewSessionListItemDTO>> list() {
    return ResponseEntity.ok(sessionService.findAll());
  }

  @PostMapping("/api/interview/sessions/{sessionId}/turns/{turnId}/answer")
  public ResponseEntity<InterviewAnswerResponse> answer(
      @PathVariable String sessionId,
      @PathVariable Long turnId,
      @RequestBody InterviewAnswerRequest request) {
    return ResponseEntity.ok(answerService.submitAnswer(sessionId, turnId, request));
  }

  @GetMapping("/api/interview/sessions/{sessionId}/resume")
  public ResponseEntity<InterviewResumeResponse> resume(@PathVariable String sessionId) {
    return ResponseEntity.ok(sessionService.resume(sessionId));
  }

  @GetMapping("/api/interview/sessions/{sessionId}/report")
  public ResponseEntity<InterviewReportStatusResponse> report(@PathVariable String sessionId) {
    return ResponseEntity.ok(evaluationService.getReport(sessionId));
  }

  @PostMapping("/api/interview/sessions/{sessionId}/evaluation/retry")
  public ResponseEntity<InterviewReportStatusResponse> retryEvaluation(
      @PathVariable String sessionId) {
    return ResponseEntity.accepted().body(evaluationService.retry(sessionId));
  }
}
