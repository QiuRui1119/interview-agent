import { API_ROUTES } from '../constants/routes'
import { request } from './request'
import type { KnowledgeBaseDetail, KnowledgeBaseItem, KnowledgeBaseUploadResponse } from '../types/knowledgebase'

export function getKnowledgeBases(signal?: AbortSignal): Promise<KnowledgeBaseItem[]> {
  return request<KnowledgeBaseItem[]>(API_ROUTES.knowledgebaseList, { signal })
}

export function getKnowledgeBase(id: number) {
  return request<KnowledgeBaseDetail>(API_ROUTES.knowledgebaseDetail(id))
}

export function uploadKnowledgeBase(file: File, name: string, category: string): Promise<KnowledgeBaseUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('name', name)
  formData.append('category', category)
  return request<KnowledgeBaseUploadResponse>(API_ROUTES.knowledgebaseUpload, { method: 'POST', body: formData })
}

export function revectorizeKnowledgeBase(id: number): Promise<KnowledgeBaseUploadResponse> {
  return request<KnowledgeBaseUploadResponse>(API_ROUTES.knowledgebaseRevectorize(id), { method: 'POST' })
}

export function deleteKnowledgeBase(id: number): Promise<void> {
  return request<void>(API_ROUTES.knowledgebaseDelete(id), { method: 'DELETE' })
}
