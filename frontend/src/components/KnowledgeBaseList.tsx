import { BookOpen, MoreHorizontal, RotateCw, Trash2, UploadCloud } from 'lucide-react'
import type { KnowledgeBaseItem } from '../types/knowledgebase'
import { KnowledgeBaseStatusBadge } from './KnowledgeBaseStatusBadge'

interface KnowledgeBaseListProps { items: KnowledgeBaseItem[]; onRevectorize: (id: number) => void; onDelete: (item: KnowledgeBaseItem) => void; busyId?: number | null; highlightedId?: number | null }

function formatDate(value: string) { return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value)) }

export function KnowledgeBaseList({ items, onRevectorize, onDelete, busyId = null, highlightedId = null }: KnowledgeBaseListProps) {
  if (!items.length) return <div className="empty-list">No knowledge documents yet.</div>
  return <div className="knowledge-list">
    {items.map((item) => {
      const canRevectorize = item.sourceType === 'USER_UPLOAD'
        ? item.vectorStatus === 'COMPLETED' || item.vectorStatus === 'FAILED'
        : item.sourceType === 'BUILT_IN' && item.vectorStatus === 'FAILED'
      const canDelete = item.sourceType === 'USER_UPLOAD' && item.vectorStatus !== 'PROCESSING'
      return <div className={`knowledge-row${highlightedId === item.id ? ' is-highlighted' : ''}`} key={item.id}>
      <span className="knowledge-row-icon">{item.sourceType === 'BUILT_IN' ? <BookOpen size={18} /> : <UploadCloud size={18} />}</span>
      <div className="knowledge-row-main"><strong>{item.name}</strong><span>{item.originalFileName} · {item.category} · {formatDate(item.uploadTime)}</span></div>
      <span className={`source-badge source-${item.sourceType.toLowerCase()}`}>{item.sourceType === 'BUILT_IN' ? 'SYSTEM' : 'USER'}</span>
      <span className="knowledge-size">{formatFileSize(item.fileSize)}</span>
      <span className="knowledge-chunks">{item.vectorChunkCount ?? 0} chunks</span>
      <KnowledgeBaseStatusBadge status={item.vectorStatus} />
      {(canRevectorize || canDelete) && <details className="knowledge-actions"><summary className="icon-button" aria-label={`Actions for ${item.name}`} title="Document actions"><MoreHorizontal size={17} /></summary><div className="knowledge-menu">
        {canRevectorize && <button type="button" onClick={() => onRevectorize(item.id)} disabled={busyId === item.id}><RotateCw size={14} /> {item.sourceType === 'BUILT_IN' ? 'Resync' : 'Revectorize'}</button>}
        {canDelete && <button type="button" onClick={() => onDelete(item)} disabled={busyId === item.id}><Trash2 size={14} /> Delete</button>}
      </div></details>}
    </div>
    })}
  </div>
}

function formatFileSize(bytes: number | null) {
  if (bytes === null || bytes === 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
