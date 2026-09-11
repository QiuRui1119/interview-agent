import { CheckSquare, FileText } from 'lucide-react'
import type { KnowledgeBaseItem } from '../types/knowledgebase'

interface KnowledgeBaseSelectorProps {
  items: KnowledgeBaseItem[]
  selectedIds: number[]
  onChange: (selectedIds: number[]) => void
}

function isSelectable(item: KnowledgeBaseItem) {
  return item.sourceType === 'USER_UPLOAD'
    && item.vectorStatus === 'COMPLETED'
    && item.enabled
}

export function KnowledgeBaseSelector({ items, selectedIds, onChange }: KnowledgeBaseSelectorProps) {
  const selectableItems = items.filter(isSelectable)
  const validSelectedIds = selectedIds.filter((id) => selectableItems.some((item) => item.id === id))

  const toggleSelection = (id: number) => {
    const nextSelectedIds = validSelectedIds.includes(id)
      ? validSelectedIds.filter((selectedId) => selectedId !== id)
      : [...validSelectedIds, id]
    onChange(nextSelectedIds)
  }

  if (selectableItems.length === 0) return null

  return <section className="knowledge-selector" aria-labelledby="knowledge-selector-title">
    <div className="section-heading">
      <div><p className="section-kicker">Interview context</p><h2 id="knowledge-selector-title">Your knowledge sources</h2></div>
      <span className="knowledge-selector-count">{validSelectedIds.length} selected</span>
    </div>
    <div className="knowledge-selector-list">
      {selectableItems.map((item) => {
        const selected = validSelectedIds.includes(item.id)
        return <label className={`knowledge-selector-item${selected ? ' is-selected' : ''}`} key={item.id}>
          <input type="checkbox" checked={selected} onChange={() => toggleSelection(item.id)} />
          <span className="knowledge-selector-check"><CheckSquare size={16} /></span>
          <FileText size={17} />
          <span className="knowledge-selector-copy"><strong>{item.name}</strong><small>{item.category} · {item.originalFileName}</small></span>
        </label>
      })}
    </div>
  </section>
}
