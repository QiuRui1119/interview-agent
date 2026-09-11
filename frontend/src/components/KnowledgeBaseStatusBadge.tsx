import { LoaderCircle } from 'lucide-react'
import type { VectorStatus } from '../types/knowledgebase'

const labels: Record<VectorStatus, string> = { PENDING: '等待向量化', PROCESSING: '正在处理', COMPLETED: '可用于面试', FAILED: '处理失败' }

export function KnowledgeBaseStatusBadge({ status }: { status: VectorStatus }) {
  const active = status === 'PENDING' || status === 'PROCESSING'
  return <span className={`kb-status kb-status-${status.toLowerCase()}`}>{active && <LoaderCircle className="spin" size={12} />}{labels[status]}</span>
}
