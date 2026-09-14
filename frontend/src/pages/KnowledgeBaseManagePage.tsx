import { AlertCircle, LoaderCircle, MessageSquareText, Plus, RefreshCw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { deleteKnowledgeBase, revectorizeKnowledgeBase } from '../api/knowledgebase'
import { DeleteConfirmDialog } from '../components/DeleteConfirmDialog'
import { KnowledgeBaseList } from '../components/KnowledgeBaseList'
import { useKnowledgeBases } from '../hooks/useKnowledgeBases'
import type { KnowledgeBaseItem } from '../types/knowledgebase'
import { Link } from 'react-router-dom'
import { PAGE_ROUTES } from '../constants/routes'

export function KnowledgeBaseManagePage() {
  const { knowledgeBases, isLoading, errorMessage, refresh } = useKnowledgeBases()
  const [confirmItem, setConfirmItem] = useState<KnowledgeBaseItem | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [sourceFilter, setSourceFilter] = useState<'ALL' | 'USER_UPLOAD' | 'BUILT_IN'>('ALL')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'COMPLETED' | 'ACTIVE' | 'FAILED'>('ALL')
  const [categoryFilter, setCategoryFilter] = useState('ALL')
  const [search, setSearch] = useState('')
  const location = useLocation()
  const routeState = location.state as { newKnowledgeBaseId?: number; duplicate?: boolean } | null

  const handleRevectorize = async (id: number) => {
    setBusyId(id); setActionError(null)
    try { await revectorizeKnowledgeBase(id); await refresh(true) }
    catch (error) { setActionError(error instanceof Error ? error.message : 'Unable to revectorize document.') }
    finally { setBusyId(null) }
  }

  const handleDelete = async () => {
    if (!confirmItem) return
    setBusyId(confirmItem.id); setActionError(null)
    try { await deleteKnowledgeBase(confirmItem.id); setConfirmItem(null); await refresh(true) }
    catch (error) { setActionError(error instanceof Error ? error.message : 'Unable to delete document.') }
    finally { setBusyId(null) }
  }

  const activeCount = knowledgeBases.filter((item) => item.vectorStatus === 'PENDING' || item.vectorStatus === 'PROCESSING').length
  const readyCount = knowledgeBases.filter((item) => item.vectorStatus === 'COMPLETED').length
  const categories = useMemo(() => Array.from(new Set(knowledgeBases.map((item) => item.category).filter(Boolean))).sort(), [knowledgeBases])
  const visibleItems = useMemo(() => knowledgeBases.filter((item) => {
    const matchesSource = sourceFilter === 'ALL' || item.sourceType === sourceFilter
    const matchesStatus = statusFilter === 'ALL'
      || (statusFilter === 'COMPLETED' && item.vectorStatus === 'COMPLETED')
      || (statusFilter === 'ACTIVE' && (item.vectorStatus === 'PENDING' || item.vectorStatus === 'PROCESSING'))
      || (statusFilter === 'FAILED' && item.vectorStatus === 'FAILED')
    const matchesCategory = categoryFilter === 'ALL' || item.category === categoryFilter
    const query = search.trim().toLowerCase()
    const matchesSearch = !query || item.originalFileName.toLowerCase().includes(query) || item.name.toLowerCase().includes(query)
    return matchesSource && matchesStatus && matchesCategory && matchesSearch
  }), [categoryFilter, knowledgeBases, search, sourceFilter, statusFilter])
  const noticeMessage = routeState?.duplicate
    ? 'This document already existed. The existing record was reused.'
    : routeState?.newKnowledgeBaseId
      ? 'Document uploaded. Vectorization is pending.'
      : null

  return <>
    <main className="kb-page">
      <div className="kb-heading"><div><p className="eyebrow">Knowledge operations</p><h1>Knowledge base</h1><p className="heading-copy">Keep interview material searchable, current, and ready for guided sessions.</p></div><div className="kb-heading-actions"><Link className="secondary-button" to={PAGE_ROUTES.knowledgebaseQuery}><MessageSquareText size={16} /> Ask knowledge</Link><Link className="primary-button" to={PAGE_ROUTES.knowledgebaseUpload}><Plus size={16} /> Upload document</Link></div></div>
      <section className="kb-summary-grid" aria-label="Knowledge base summary"><div><span>Total sources</span><strong>{knowledgeBases.length}</strong></div><div><span>Ready for interview</span><strong>{readyCount}</strong></div><div><span>In progress</span><strong>{activeCount}</strong></div></section>
      {errorMessage && <div className="alert error-alert"><AlertCircle size={17} /><span>{errorMessage}</span></div>}
      {actionError && <div className="alert error-alert"><AlertCircle size={17} /><span>{actionError}</span></div>}
      {noticeMessage && <div className="alert notice-alert"><span>{noticeMessage}</span></div>}
      <section className="kb-panel"><div className="section-heading"><div><p className="section-kicker">Source library</p><h2>Documents</h2></div><button className="secondary-button" type="button" onClick={() => void refresh()} disabled={isLoading}><RefreshCw className={isLoading ? 'spin' : ''} size={15} /> Refresh</button></div>
        <div className="kb-filters"><label className="kb-search"><span className="sr-only">Search file name</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search file name" /></label><label><span className="sr-only">Source</span><select value={sourceFilter} onChange={(event) => setSourceFilter(event.target.value as typeof sourceFilter)}><option value="ALL">All sources</option><option value="USER_UPLOAD">User</option><option value="BUILT_IN">System</option></select></label><label><span className="sr-only">Status</span><select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)}><option value="ALL">All status</option><option value="COMPLETED">Available</option><option value="ACTIVE">Processing</option><option value="FAILED">Failed</option></select></label><label><span className="sr-only">Category</span><select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="ALL">All categories</option>{categories.map((category) => <option key={category} value={category}>{category}</option>)}</select></label></div>
        <div className="kb-result-count">Showing {visibleItems.length} of {knowledgeBases.length} documents</div>
        {isLoading && knowledgeBases.length === 0 ? <div className="loading-state"><LoaderCircle className="spin" size={18} /> Loading knowledge base...</div> : <KnowledgeBaseList items={visibleItems} onRevectorize={(id) => void handleRevectorize(id)} onDelete={setConfirmItem} busyId={busyId} highlightedId={routeState?.newKnowledgeBaseId} />}
      </section>
    </main>
    {confirmItem && <DeleteConfirmDialog title={`Delete ${confirmItem.name}?`} description="This removes the uploaded file, its vectors, and the database record. Built-in material is never deleted here." isBusy={busyId === confirmItem.id} onConfirm={() => void handleDelete()} onCancel={() => setConfirmItem(null)} />}
  </>
}
