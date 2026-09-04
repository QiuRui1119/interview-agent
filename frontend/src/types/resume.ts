export type AnalysisStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface ResumeSummary {
  id: number
  fileName: string
  analysisStatus: AnalysisStatus
}

export interface ResumeUploadResponse {
  resume: ResumeSummary
  storage: {
    fileKey: string
    fileUrl: string
    resumeId: number
  }
  duplicate: boolean
}

export interface ResumeAnalysisStatusResponse {
  status: AnalysisStatus
  error: string | null
}

export interface ResumeListItem {
  id: number
  filename: string
  fileSize: number | null
  uploadedAt: string
  accessCount: number
  latestScore: number | null
  lastAnalyzedAt: string | null
  interviewCount: number
  analyzeStatus: AnalysisStatus
  analyzeError: string | null
}

export interface ResumeDetail {
  id: number
  filename: string
  fileSize: number | null
  uploadedAt: string
  lastAccessTime: string | null
  accessCount: number
  storageKey: string | null
  storageUrl: string | null
  resumeText: string | null
  analyzeStatus: AnalysisStatus
  analyzeError: string | null
}

export interface ResumeAnalysisResponse {
  overallScore: number
  scoreDetail: {
    contentScore: number
    structureScore: number
    skillMatchScore: number
    expressionScore: number
    projectScore: number
  }
  summary: string
  strengths: string[]
  suggestions: Array<{
    category: string
    priority: string
    issue: string
    recommendation: string
  }>
  originalText: string
}
