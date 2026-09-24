import { useCallback, useEffect, useRef, useState } from 'react'
import { getInterviewReport } from '../api/interview'
import { ApiError } from '../api/request'
import type { InterviewReportStatusResponse } from '../types/interview'

const POLL_MS = 2000
const MAX_RETRY_MS = 30000

function shouldPoll(data: InterviewReportStatusResponse | null) {
  return data?.status === 'PENDING' || data?.status === 'PROCESSING'
}

export function useInterviewReport(sessionId: string, enabled: boolean) {
  const [data, setData] = useState<InterviewReportStatusResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<number | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const requestVersionRef = useRef(0)
  const retryDelayRef = useRef(POLL_MS)
  const dataRef = useRef<InterviewReportStatusResponse | null>(null)

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

  const load = useCallback(async () => {
    if (!enabled) return
    clearTimer()
    const version = ++requestVersionRef.current
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    try {
      const next = await getInterviewReport(sessionId, controller.signal)
      if (version !== requestVersionRef.current || controller.signal.aborted) return
      dataRef.current = next
      setData(next)
      setError(null)
      retryDelayRef.current = POLL_MS
      if (document.visibilityState === 'visible' && shouldPoll(next)) {
        timerRef.current = window.setTimeout(() => void load(), POLL_MS)
      }
    } catch (requestError) {
      if (version !== requestVersionRef.current || controller.signal.aborted) return
      setError(requestError instanceof Error ? requestError.message : 'Failed to load report.')
      const retryable = !(requestError instanceof ApiError)
        || requestError.status === 429 || requestError.status >= 500
      if (retryable && document.visibilityState === 'visible') {
        const delay = retryDelayRef.current
        retryDelayRef.current = Math.min(delay * 2, MAX_RETRY_MS)
        timerRef.current = window.setTimeout(() => void load(), delay)
      }
    }
  }, [clearTimer, enabled, sessionId])

  useEffect(() => {
    setData(null)
    dataRef.current = null
    setError(null)
    retryDelayRef.current = POLL_MS
    if (enabled) void load()
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        clearTimer()
        invalidateRequests()
        abortActiveRequest()
      } else if (enabled && (dataRef.current === null || shouldPoll(dataRef.current))) {
        void load()
      }
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      clearTimer()
      invalidateRequests()
      abortActiveRequest()
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [abortActiveRequest, clearTimer, enabled, invalidateRequests, load])

  return { data, error, refresh: load }
}
