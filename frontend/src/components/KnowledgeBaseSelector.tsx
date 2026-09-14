import { BookOpen, CheckSquare, FileText, Square } from 'lucide-react'
import type { KnowledgeBaseItem } from '../types/knowledgebase'

interface KnowledgeBaseSelectorProps {
  items: KnowledgeBaseItem[]
  selectedIds: number[]
  onChange: (selectedIds: number[]) => void
  sourceFilter?: 'ALL' | 'USER_UPLOAD'
  title?: string
  eyebrow?: string
}

function isSelectable(item: KnowledgeBaseItem, sourceFilter: 'ALL' | 'USER_UPLOAD') {
  return item.vectorStatus === 'COMPLETED'
    && item.enabled
    && (sourceFilter === 'ALL' || item.sourceType === 'USER_UPLOAD')
}

export function KnowledgeBaseSelector({
  items,
  selectedIds,
  onChange,
  sourceFilter = 'ALL',
  title = 'Knowledge sources',
  eyebrow = 'Answer context',
}: KnowledgeBaseSelectorProps) {
  const selectableItems = items.filter((item) => isSelectable(item, sourceFilter))
  const validSelectedIds = selectedIds.filter((id) => selectableItems.some((item) => item.id === id))

  const toggleSelection = (id: number) => {
    const nextSelectedIds = validSelectedIds.includes(id)
      ? validSelectedIds.filter((selectedId) => selectedId !== id)
      : [...validSelectedIds, id]
    onChange(nextSelectedIds)
  }

  return <section className="knowledge-selector" aria-labelledby="knowledge-selector-title">
    <div className="section-heading">
      <div><p className="section-kicker">{eyebrow}</p><h2 id="knowledge-selector-title">{title}</h2></div>
      <span className="knowledge-selector-count">{validSelectedIds.length} selected</span>
    </div>
    {selectableItems.length === 0 ? <p className="knowledge-selector-empty">No indexed knowledge sources are available.</p> : <div className="knowledge-selector-list">
      {selectableItems.map((item) => {
        const selected = validSelectedIds.includes(item.id)
        return <label className={`knowledge-selector-item${selected ? ' is-selected' : ''}`} key={item.id}>
          <input type="checkbox" checked={selected} onChange={() => toggleSelection(item.id)} />
          <span className="knowledge-selector-check">{selected ? <CheckSquare size={16} /> : <Square size={16} />}</span>
          {item.sourceType === 'BUILT_IN' ? <BookOpen size={17} /> : <FileText size={17} />}
          <span className="knowledge-selector-copy"><strong>{item.name}</strong><small>{item.category} · {item.sourceType === 'BUILT_IN' ? 'System' : 'Uploaded'}</small></span>
        </label>
      })}
    </div>}
  </section>
}
