import { useCallback, useEffect, useRef, useState } from 'react'
import { getKnowledgeBases } from '../api/knowledgebase'
import type { KnowledgeBaseItem } from '../types/knowledgebase'

const POLL_DELAY_MS = 3000

function hasChanged(previous: KnowledgeBaseItem[], next: KnowledgeBaseItem[]) {
  if (previous.length !== next.length) return true
  return next.some((item, index) => {
    const oldItem = previous[index]
    return oldItem.id !== item.id || oldItem.vectorStatus !== item.vectorStatus
      || oldItem.vectorError !== item.vectorError || oldItem.vectorChunkCount !== item.vectorChunkCount
      || oldItem.enabled !== item.enabled
  })
}

export function useKnowledgeBases() {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const activeRef = useRef(false)
  const timerRef = useRef<number | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const disposedRef = useRef(false)

  const clearTimer = () => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }

  const refresh = useCallback(async (silent = false) => {
    clearTimer()
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    if (!silent) setIsLoading(true)
    try {
      const next = await getKnowledgeBases(controller.signal)
      activeRef.current = next.some((item) => item.vectorStatus === 'PENDING' || item.vectorStatus === 'PROCESSING')
      setKnowledgeBases((previous) => hasChanged(previous, next) ? next : previous)
      setErrorMessage(null)
      return next
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return null
      if (silent) console.error('Knowledge base polling failed.', error)
      else setErrorMessage(error instanceof Error ? error.message : 'Unable to load knowledge bases.')
      return null
    } finally {
      if (!silent) setIsLoading(false)
      if (controllerRef.current === controller) controllerRef.current = null
      if (!disposedRef.current && document.visibilityState === 'visible' && activeRef.current) {
        timerRef.current = window.setTimeout(() => void refresh(true), POLL_DELAY_MS)
      }
    }
  }, [])

  useEffect(() => {
    disposedRef.current = false
    void refresh()

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        clearTimer()
        controllerRef.current?.abort()
      } else {
        void refresh(true)
      }
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      disposedRef.current = true
      clearTimer()
      controllerRef.current?.abort()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [refresh])

  return { knowledgeBases, isLoading, errorMessage, refresh }
}
