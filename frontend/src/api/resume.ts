import { API_ROUTES } from '../constants/routes'
import type {
  ResumeAnalysisStatusResponse,
  ResumeAnalysisResponse,
  ResumeDetail,
  ResumeListItem,
  ResumeUploadResponse,
} from '../types/resume'
import { request } from './request'

export function uploadResume(file: File): Promise<ResumeUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)

  return request<ResumeUploadResponse>(API_ROUTES.resumeUpload, {
    method: 'POST',
    body: formData,
  })
}

export function getResumes(): Promise<ResumeListItem[]> {
  return request<ResumeListItem[]>(API_ROUTES.resumes)
}

export function getResumeDetail(resumeId: number): Promise<ResumeDetail> {
  return request<ResumeDetail>(API_ROUTES.resumeDetail(resumeId))
}

export function getResumeAnalysis(resumeId: number): Promise<ResumeAnalysisResponse | null> {
  return request<ResumeAnalysisResponse | null>(API_ROUTES.resumeAnalysis(resumeId))
}

export function deleteResume(resumeId: number): Promise<void> {
  return request<void>(API_ROUTES.resumeDelete(resumeId), {
    method: 'DELETE',
  })
}

export function reanalyzeResume(resumeId: number): Promise<ResumeAnalysisStatusResponse> {
  return request<ResumeAnalysisStatusResponse>(API_ROUTES.resumeReanalyze(resumeId), {
    method: 'POST',
  })
}
