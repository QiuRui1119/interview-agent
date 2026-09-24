import { useCallback, useEffect, useRef, useState } from 'react'
import { getInterviewSessions } from '../api/interview'
import type { InterviewSessionListItem } from '../types/interview'

const POLL_MS = 3000

function hasActiveTasks(items: InterviewSessionListItem[]) {
  return items.some((item) => item.generationStatus === 'PENDING'
    || item.generationStatus === 'PROCESSING'
    || item.evaluateStatus === 'PENDING'
    || item.evaluateStatus === 'PROCESSING')
}

export function useInterviewSessions() {
  const [items, setItems] = useState<InterviewSessionListItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const itemsRef = useRef<InterviewSessionListItem[]>([])
  const timerRef = useRef<number | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const requestVersionRef = useRef(0)

  const abortActiveRequest = useCallback(() => {
    controllerRef.current?.abort()
  }, [])
  const invalidateRequests = useCallback(() => {
    requestVersionRef.current++
  }, [])

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    timerRef.current = null
  }, [])

  const load = useCallback(async (silent = false) => {
    clearTimer()
    const version = ++requestVersionRef.current
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    if (!silent) setIsLoading(true)
    try {
      const next = await getInterviewSessions(controller.signal)
      if (version !== requestVersionRef.current || controller.signal.aborted) return
      itemsRef.current = next
      setItems(next)
      setError(null)
      if (document.visibilityState === 'visible' && hasActiveTasks(next)) {
        timerRef.current = window.setTimeout(() => void load(true), POLL_MS)
      }
    } catch (requestError) {
      if (version === requestVersionRef.current && !controller.signal.aborted) {
        setError(requestError instanceof Error ? requestError.message : 'Unable to load interviews.')
      }
    } finally {
      if (version === requestVersionRef.current && !controller.signal.aborted && !silent) {
        setIsLoading(false)
      }
    }
  }, [clearTimer])

  useEffect(() => {
    void load()
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        clearTimer()
        invalidateRequests()
        abortActiveRequest()
      } else if (itemsRef.current.length === 0 || hasActiveTasks(itemsRef.current)) {
        void load(true)
      }
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      clearTimer()
      invalidateRequests()
      abortActiveRequest()
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [abortActiveRequest, clearTimer, invalidateRequests, load])

  return { items, isLoading, error, refresh: () => load(true) }
}
