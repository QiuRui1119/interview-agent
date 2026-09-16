import { AlertCircle, BookOpen, CircleStop, Eraser, LoaderCircle, Send } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { streamKnowledgeBaseQuery } from '../api/knowledgebase'
import { KnowledgeBaseSelector } from '../components/KnowledgeBaseSelector'
import { RagMessageList } from '../components/RagMessageList'
import { PAGE_ROUTES } from '../constants/routes'
import { useKnowledgeBases } from '../hooks/useKnowledgeBases'
import type { RagMessage } from '../types/knowledgebase'

function messageId(prefix: string) {
  return `${prefix}-${crypto.randomUUID()}`
}

export function KnowledgeBaseQueryPage() {
  const { knowledgeBases, isLoading, errorMessage } = useKnowledgeBases()
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [messages, setMessages] = useState<RagMessage[]>([])
  const [question, setQuestion] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [queryError, setQueryError] = useState<string | null>(null)
  const defaultsAppliedRef = useRef(false)
  const controllerRef = useRef<AbortController | null>(null)
  const messageViewportRef = useRef<HTMLDivElement>(null)

  const availableKnowledgeBases = useMemo(() => knowledgeBases.filter((item) => (
    item.vectorStatus === 'COMPLETED' && item.enabled
  )), [knowledgeBases])

  useEffect(() => {
    const availableIds = new Set(availableKnowledgeBases.map((item) => item.id))
    setSelectedIds((current) => current.filter((id) => availableIds.has(id)))
    if (!defaultsAppliedRef.current && availableKnowledgeBases.length > 0) {
      defaultsAppliedRef.current = true
      setSelectedIds(availableKnowledgeBases
        .filter((item) => item.sourceType === 'BUILT_IN')
        .map((item) => item.id))
    }
  }, [availableKnowledgeBases])

  useEffect(() => {
    const viewport = messageViewportRef.current
    if (viewport) viewport.scrollTo({ top: viewport.scrollHeight, behavior: 'smooth' })
  }, [messages])

  useEffect(() => () => controllerRef.current?.abort(), [])

  const updateAssistant = useCallback((id: string, update: (message: RagMessage) => RagMessage) => {
    setMessages((current) => current.map((message) => message.id === id ? update(message) : message))
  }, [])

  const stopStreaming = () => {
    const controller = controllerRef.current
    if (!controller) return
    controllerRef.current = null
    controller.abort()
    setIsStreaming(false)
    setQueryError('Response stopped. You can submit another question.')
    setMessages((current) => current.map((message) => message.status === 'streaming'
      ? { ...message, status: 'error', content: message.content || 'Response stopped before content was received.' }
      : message))
  }

  const submitQuestion = async () => {
    const normalizedQuestion = question.trim()
    if (isStreaming || !normalizedQuestion) return
    if (selectedIds.length === 0) {
      setQueryError('Select at least one indexed knowledge source.')
      return
    }

    const userMessage: RagMessage = {
      id: messageId('user'),
      role: 'user',
      content: normalizedQuestion,
      status: 'complete',
      sources: [],
    }
    const assistantId = messageId('assistant')
    const assistantMessage: RagMessage = {
      id: assistantId,
      role: 'assistant',
      content: '',
      status: 'streaming',
      sources: [],
    }

    setMessages((current) => [...current, userMessage, assistantMessage])
    setQuestion('')
    setQueryError(null)
    setIsStreaming(true)
    const controller = new AbortController()
    controllerRef.current = controller
    let completionReceived = false
    let streamFailed = false
    const history = messages
      .filter((message) => message.status === 'complete' && message.content.trim())
      .map((message) => ({ role: message.role, content: message.content }))

    try {
      await streamKnowledgeBaseQuery({
        knowledgeBaseIds: selectedIds,
        question: normalizedQuestion,
        history,
      }, {
        onText: (text) => updateAssistant(assistantId, (message) => ({
          ...message,
          content: message.content + text,
        })),
        onSources: (sources) => updateAssistant(assistantId, (message) => ({ ...message, sources })),
        onError: (message) => {
          streamFailed = true
          setQueryError(message)
          updateAssistant(assistantId, (current) => ({
            ...current,
            content: current.content || message,
            status: 'error',
          }))
        },
        onComplete: (success) => {
          completionReceived = true
          if (!success) streamFailed = true
          updateAssistant(assistantId, (message) => ({
            ...message,
            status: success && !streamFailed ? 'complete' : 'error',
          }))
        },
      }, controller.signal)

      if (!completionReceived && !streamFailed) {
        throw new Error('The response stream ended unexpectedly. Please ask again.')
      }
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        const message = error instanceof Error ? error.message : 'Knowledge base query failed.'
        setQueryError(message)
        updateAssistant(assistantId, (current) => ({
          ...current,
          content: current.content || message,
          status: 'error',
        }))
      }
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
      setIsStreaming(false)
    }
  }

  return <main className="rag-page">
    <div className="kb-heading rag-heading">
      <div>
        <p className="eyebrow">Grounded answers</p>
        <h1>Ask your knowledge</h1>
        <p className="heading-copy">Answers are generated only from the indexed sources you select.</p>
      </div>
      <Link className="secondary-button" to={PAGE_ROUTES.knowledgebase}><BookOpen size={16} /> Manage sources</Link>
    </div>

    {errorMessage && <div className="alert error-alert"><AlertCircle size={17} /><span>{errorMessage}</span></div>}
    {queryError && <div className="alert error-alert"><AlertCircle size={17} /><span>{queryError}</span></div>}

    <div className="rag-workspace">
      <aside className="rag-source-panel">
        {isLoading && knowledgeBases.length === 0
          ? <div className="loading-state"><LoaderCircle className="spin" size={18} /> Loading sources...</div>
          : <KnowledgeBaseSelector
              items={availableKnowledgeBases}
              selectedIds={selectedIds}
              onChange={setSelectedIds}
            />}
        <p className="rag-source-note">System material is selected by default. You can combine it with uploaded documents.</p>
      </aside>

      <section className="rag-conversation" aria-label="Knowledge base conversation">
        <header className="rag-conversation-header">
          <div><p className="section-kicker">Conversation</p><h2>Technical Q&amp;A</h2></div>
          {messages.length > 0 && <button className="icon-button" type="button" title="Clear conversation" aria-label="Clear conversation" disabled={isStreaming} onClick={() => { setMessages([]); setQueryError(null) }}><Eraser size={16} /></button>}
        </header>
        <div className="rag-message-viewport" ref={messageViewportRef}>
          <RagMessageList messages={messages} />
        </div>
        <div className="rag-composer">
          <textarea
            value={question}
            rows={3}
            maxLength={2000}
            placeholder="Ask a technical question from the selected sources..."
            disabled={isStreaming}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                void submitQuestion()
              }
            }}
          />
          <div className="rag-composer-footer">
            <span>{selectedIds.length} source{selectedIds.length === 1 ? '' : 's'} · {question.length}/2000</span>
            {isStreaming
              ? <button className="secondary-button" type="button" onClick={stopStreaming}><CircleStop size={16} /> Stop</button>
              : <button className="primary-button" type="button" disabled={!question.trim() || selectedIds.length === 0} onClick={() => void submitQuestion()}><Send size={16} /> Ask</button>}
          </div>
        </div>
      </section>
    </div>
  </main>
}
