import { routes } from '../constants/routes'
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

  return request<ResumeUploadResponse>(routes.resumeUpload, {
    method: 'POST',
    body: formData,
  })
}

export function getResumes(): Promise<ResumeListItem[]> {
  return request<ResumeListItem[]>(routes.resumes)
}

export function getResumeDetail(resumeId: number): Promise<ResumeDetail> {
  return request<ResumeDetail>(routes.resumeDetail(resumeId))
}

export function getResumeAnalysis(resumeId: number): Promise<ResumeAnalysisResponse | null> {
  return request<ResumeAnalysisResponse | null>(routes.resumeAnalysis(resumeId))
}

export function deleteResume(resumeId: number): Promise<void> {
  return request<void>(routes.resumeDelete(resumeId), {
    method: 'DELETE',
  })
}

export function reanalyzeResume(resumeId: number): Promise<ResumeAnalysisStatusResponse> {
  return request<ResumeAnalysisStatusResponse>(routes.resumeReanalyze(resumeId), {
    method: 'POST',
  })
}
