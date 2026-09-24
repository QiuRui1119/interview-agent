import { API_ROUTES } from '../constants/routes'
import { request } from './request'
import type { InterviewAnswerResponse, InterviewReportStatusResponse, InterviewResumeResponse, InterviewSessionListItem } from '../types/interview'
import type { InterviewSessionStatus } from '../types/interview'

export interface CreateInterviewSessionRequest {
  resumeId: number
  skillId: string
  difficulty: string
  knowledgeBaseIds: number[]
  topicCount: number
  maxFollowUpsPerTopic: number
  maxTurns: number
}

export interface CreateInterviewSessionResponse {
  sessionId: string
  status: InterviewSessionStatus
}

export function createInterviewSession(
  payload: CreateInterviewSessionRequest,
): Promise<CreateInterviewSessionResponse> {
  return request<CreateInterviewSessionResponse>(API_ROUTES.interviewCreate, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function getInterviewSessions(signal?: AbortSignal): Promise<InterviewSessionListItem[]> {
  return request<InterviewSessionListItem[]>(API_ROUTES.interviewList, { signal })
}

export function resumeInterview(sessionId: string, signal?: AbortSignal): Promise<InterviewResumeResponse> {
  return request<InterviewResumeResponse>(API_ROUTES.interviewResume(sessionId), { signal })
}

export function getInterviewReport(
  sessionId: string,
  signal?: AbortSignal,
): Promise<InterviewReportStatusResponse> {
  return request<InterviewReportStatusResponse>(API_ROUTES.interviewReport(sessionId), { signal })
}

export function retryInterviewEvaluation(sessionId: string): Promise<InterviewReportStatusResponse> {
  return request<InterviewReportStatusResponse>(API_ROUTES.interviewEvaluationRetry(sessionId), {
    method: 'POST',
  })
}

export function submitInterviewAnswer(
  sessionId: string,
  turnId: number,
  requestId: string,
  answer: string,
): Promise<InterviewAnswerResponse> {
  return request<InterviewAnswerResponse>(API_ROUTES.interviewAnswer(sessionId, turnId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ requestId, answer }),
  })
}
