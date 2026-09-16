export type KnowledgeSourceType = 'USER_UPLOAD' | 'BUILT_IN'
export type VectorStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface KnowledgeBaseItem {
  id: number
  name: string
  category: string
  originalFileName: string
  fileSize: number | null
  sourceType: KnowledgeSourceType
  vectorStatus: VectorStatus
  vectorError: string | null
  vectorChunkCount: number
  uploadTime: string
  enabled: boolean
}

export interface KnowledgeBaseListItem extends KnowledgeBaseItem {}

export interface KnowledgeBaseDetail extends KnowledgeBaseItem {
  contentType: string | null
  sourceKey: string | null
  contentHash: string | null
  version: number
  enabled: boolean
}

export interface KnowledgeBaseUploadResponse {
  id: number
  name: string
  sourceType: KnowledgeSourceType
  vectorStatus: VectorStatus
  duplicate: boolean
}

export interface KnowledgeBaseQueryRequest {
  knowledgeBaseIds: number[]
  question: string
  history?: KnowledgeBaseQueryHistoryMessage[]
}

export interface KnowledgeBaseQueryHistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface KnowledgeBaseQuerySource {
  sourceName: string
  sourceType: KnowledgeSourceType
  category: string | null
  chunkIndex: number | null
  sourceKey: string | null
  knowledgeBaseId: number | null
  version: number | null
  score: number
  chunkId: string
}

export interface KnowledgeBaseQueryResponse {
  answer: string
  sources: KnowledgeBaseQuerySource[]
}

export type RagMessageStatus = 'streaming' | 'complete' | 'error'

export interface RagMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  status: RagMessageStatus
  sources: KnowledgeBaseQuerySource[]
}
