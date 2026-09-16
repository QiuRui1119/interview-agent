import { AlertCircle, BookOpen, LoaderCircle, Sparkles, UserRound } from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { RagMessage } from '../types/knowledgebase'

interface RagMessageListProps {
  messages: RagMessage[]
}

export function RagMessageList({ messages }: RagMessageListProps) {
  if (messages.length === 0) {
    return <div className="rag-empty-state">
      <span className="rag-empty-icon"><Sparkles size={22} /></span>
      <strong>Ask from your indexed material</strong>
      <p>Select one or more sources, then ask a focused technical question.</p>
    </div>
  }

  return <div className="rag-message-list" aria-live="polite">
    {messages.map((message) => {
      const isNoResult = message.role === 'assistant'
        && message.content.includes('知识库中没有相关信息')
      return <article
        className={`rag-message rag-message-${message.role}${isNoResult ? ' is-no-result' : ''}`}
        key={message.id}
      >
        <span className="rag-message-avatar" aria-hidden="true">
          {message.role === 'user' ? <UserRound size={16} /> : <Sparkles size={16} />}
        </span>
        <div className="rag-message-body">
          <div className="rag-message-meta">
            <strong>{message.role === 'user' ? 'You' : 'Knowledge assistant'}</strong>
            {message.status === 'streaming' && <span><LoaderCircle className="spin" size={12} /> Generating</span>}
            {message.status === 'error' && <span className="rag-error-label"><AlertCircle size={12} /> Interrupted</span>}
          </div>
          <div className="rag-markdown">
            {message.content
              ? <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
              : <span className="rag-stream-placeholder">Searching selected sources...</span>}
          </div>
          {message.sources.length > 0 && <div className="rag-sources">
            <span className="rag-sources-title"><BookOpen size={13} /> Sources</span>
            <div className="rag-source-list">
              {message.sources.map((source) => <span
                className="rag-source-chip"
                key={`${source.chunkId}-${source.sourceName}`}
                title={`${source.category ?? 'Uncategorized'} · Chunk ${source.chunkIndex ?? source.chunkId} · KB ${source.knowledgeBaseId ?? '-'} · v${source.version ?? '-'}`}
              >
                <strong>{source.sourceName}</strong>
                <small>{source.sourceType === 'BUILT_IN' ? 'System' : 'Uploaded'} · {source.category ?? 'Uncategorized'} · {Math.round(source.score * 100)}%</small>
              </span>)}
            </div>
          </div>}
        </div>
      </article>
    })}
  </div>
}
