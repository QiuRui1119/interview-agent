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
