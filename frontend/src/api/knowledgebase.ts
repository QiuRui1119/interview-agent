import { API_ROUTES } from '../constants/routes'
import { ApiError, request } from './request'
import type {
  KnowledgeBaseDetail,
  KnowledgeBaseItem,
  KnowledgeBaseQueryRequest,
  KnowledgeBaseQueryResponse,
  KnowledgeBaseQuerySource,
  KnowledgeBaseUploadResponse,
} from '../types/knowledgebase'

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

export function queryKnowledgeBase(query: KnowledgeBaseQueryRequest): Promise<KnowledgeBaseQueryResponse> {
  return request<KnowledgeBaseQueryResponse>(API_ROUTES.knowledgebaseQuery, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(query),
  })
}

interface KnowledgeBaseStreamHandlers {
  onText: (text: string) => void
  onSources: (sources: KnowledgeBaseQuerySource[]) => void
  onError: (message: string) => void
  onComplete: (success: boolean) => void
}

export async function streamKnowledgeBaseQuery(
  query: KnowledgeBaseQueryRequest,
  handlers: KnowledgeBaseStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(API_ROUTES.knowledgebaseQueryStream, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(query),
    signal,
  })

  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status)
  }
  if (!response.body) throw new Error('The server did not return a response stream.')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const processBlock = (block: string) => {
    let eventName = 'message'
    const data: string[] = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
    }
    const payload = data.join('\n')
    if (eventName === 'text') handlers.onText(payload)
    else if (eventName === 'sources') handlers.onSources(parseSources(payload))
    else if (eventName === 'error') handlers.onError(payload || 'Knowledge base query failed.')
    else if (eventName === 'complete') handlers.onComplete(payload === 'true')
  }

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    let boundary = buffer.search(/\r?\n\r?\n/)
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary)
      const separator = buffer.slice(boundary).match(/^\r?\n\r?\n/)?.[0] ?? '\n\n'
      buffer = buffer.slice(boundary + separator.length)
      if (block.trim()) processBlock(block)
      boundary = buffer.search(/\r?\n\r?\n/)
    }
    if (done) break
  }
  if (buffer.trim()) processBlock(buffer)
}

function parseSources(payload: string): KnowledgeBaseQuerySource[] {
  try {
    const parsed: unknown = JSON.parse(payload)
    return Array.isArray(parsed) ? parsed as KnowledgeBaseQuerySource[] : []
  } catch {
    return []
  }
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const payload: unknown = await response.json()
    if (payload && typeof payload === 'object') {
      const record = payload as Record<string, unknown>
      return String(record.message ?? record.error ?? 'Knowledge base query failed.')
    }
  } catch {
    // Use the stable fallback below for malformed error responses.
  }
  return 'Knowledge base query failed.'
}
