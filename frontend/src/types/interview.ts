export const INTERVIEW_SESSION_STATUS = {
  CREATED: 'CREATED',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  ABORTED: 'ABORTED',
} as const

export type InterviewSessionStatus =
  typeof INTERVIEW_SESSION_STATUS[keyof typeof INTERVIEW_SESSION_STATUS]

export const INTERVIEW_TOPIC_STATUS = {
  PENDING: 'PENDING',
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
} as const

export type InterviewTopicStatus =
  typeof INTERVIEW_TOPIC_STATUS[keyof typeof INTERVIEW_TOPIC_STATUS]

export const INTERVIEW_TURN_STATUS = {
  CREATED: 'CREATED',
  ANSWER_PENDING: 'ANSWER_PENDING',
  DECIDING: 'DECIDING',
  DECIDED: 'DECIDED',
} as const

export type InterviewTurnStatus =
  typeof INTERVIEW_TURN_STATUS[keyof typeof INTERVIEW_TURN_STATUS]

export type InterviewGenerationStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type InterviewEvaluationStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export const INTERVIEW_SESSION_STATUS_VALUES = Object.values(INTERVIEW_SESSION_STATUS)
export const INTERVIEW_TOPIC_STATUS_VALUES = Object.values(INTERVIEW_TOPIC_STATUS)
export const INTERVIEW_TURN_STATUS_VALUES = Object.values(INTERVIEW_TURN_STATUS)

export interface InterviewTurnCache {
  id: number
  turnOrder: number
  question: string
  answer: string | null
  turnType: string
  parentTurnId: number | null
  decision: string | null
  status: InterviewTurnStatus
}

export interface InterviewRecentTurn {
  id: number
  question: string
  answer: string | null
  decision: string | null
}

export interface InterviewResumeResponse {
  sessionId: string
  status: InterviewSessionStatus
  generationStatus: InterviewGenerationStatus | null
  evaluateStatus: InterviewEvaluationStatus | null
  evaluateError: string | null
  currentTopicOrder: number | null
  completedTopicCount: number
  totalTurns: number
  currentTurn: InterviewTurnCache | null
  recentTurns: InterviewRecentTurn[]
}

export interface InterviewAnswerResponse {
  requestId: string
  action: 'FOLLOW_UP' | 'NEXT_TOPIC' | 'END' | 'PENDING'
  nextTurnId: number | null
  turnStatus: InterviewTurnStatus
  sessionStatus: InterviewSessionStatus
}

export interface InterviewTopicScore {
  topicId: number
  topicOrder: number
  topicName: string
  score: number
  feedback: string
  coveredPoints: string[]
  missingPoints: string[]
}

export interface InterviewTurnEvaluation {
  turnId: number
  turnOrder: number
  topicId: number
  question: string
  answer: string
  turnType: string
  parentTurnId: number | null
  score: number
  coveredPoints: string[]
  missingPoints: string[]
  feedback: string
}

export interface InterviewSessionListItem {
  sessionId: string
  resumeId: number
  resumeFilename: string | null
  skillId: string
  difficulty: string
  status: InterviewSessionStatus
  generationStatus: InterviewGenerationStatus
  evaluateStatus: InterviewEvaluationStatus | null
  overallScore: number | null
  createdAt: string
  completedAt: string | null
}

export interface InterviewReport {
  sessionId: string
  overallScore: number
  overallFeedback: string
  strengths: string[]
  improvements: string[]
  topicScores: InterviewTopicScore[]
  turnEvaluations: InterviewTurnEvaluation[]
  evaluatedAt: string
}

export interface InterviewReportStatusResponse {
  status: InterviewEvaluationStatus
  error: string | null
  report: InterviewReport | null
}
