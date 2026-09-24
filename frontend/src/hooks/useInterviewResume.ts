import { useCallback, useEffect, useRef, useState } from 'react'
import { resumeInterview } from '../api/interview'
import { ApiError } from '../api/request'
import type { InterviewResumeResponse } from '../types/interview'

const POLL_MS = 2000
const MAX_RETRY_MS = 30000

function shouldPoll(data: InterviewResumeResponse | null, waitingTurnId: number | null) {
  if (!data || data.status === 'ABORTED') return false
  if (data.generationStatus === 'PENDING' || data.generationStatus === 'PROCESSING') return true
  if (data.status === 'COMPLETED') return false
  return data.status === 'IN_PROGRESS'
    && (data.currentTurn?.status === 'DECIDING'
    || (data.currentTurn?.status === 'ANSWER_PENDING' && data.currentTurn.answer !== null)
    || (waitingTurnId !== null && data.currentTurn?.id === waitingTurnId))
}

export function useInterviewResume(sessionId: string, waitingTurnId: number | null = null) {
  const [data, setData] = useState<InterviewResumeResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<number | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const requestVersion = useRef(0)
  const dataRef = useRef<InterviewResumeResponse | null>(null)
  const retryDelayRef = useRef(POLL_MS)
  const waitingTurnIdRef = useRef(waitingTurnId)
  waitingTurnIdRef.current = waitingTurnId

  const abortActiveRequest = useCallback(() => {
    controllerRef.current?.abort()
  }, [])
  const invalidateRequests = useCallback(() => {
    requestVersion.current++
  }, [])

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    timerRef.current = null
  }, [])

  const load = useCallback(async (silent = false) => {
    clearTimer()
    const version = ++requestVersion.current
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    if (!silent) setIsLoading(true)
    try {
      const next = await resumeInterview(sessionId, controller.signal)
      if (version === requestVersion.current && !controller.signal.aborted) {
        dataRef.current = next
        setData(next)
        setError(null)
        retryDelayRef.current = POLL_MS
        if (document.visibilityState === 'visible' && shouldPoll(next, waitingTurnIdRef.current)) {
          timerRef.current = window.setTimeout(() => void load(true), POLL_MS)
        }
        return next
      }
    } catch (requestError) {
      if (version === requestVersion.current && !controller.signal.aborted) {
        setError(requestError instanceof Error ? requestError.message : 'Failed to resume interview.')
        const retryable = !(requestError instanceof ApiError)
          || requestError.status === 429 || requestError.status >= 500
        if (retryable && document.visibilityState === 'visible'
          && (dataRef.current === null || shouldPoll(dataRef.current, waitingTurnIdRef.current))) {
          const delay = retryDelayRef.current
          retryDelayRef.current = Math.min(delay * 2, MAX_RETRY_MS)
          timerRef.current = window.setTimeout(() => void load(true), delay)
        }
      }
    } finally {
      if (version === requestVersion.current && !controller.signal.aborted) {
        controllerRef.current = null
        if (!silent) setIsLoading(false)
      }
    }
  }, [clearTimer, sessionId])

  useEffect(() => {
    dataRef.current = null
    retryDelayRef.current = POLL_MS
    setData(null)
    setError(null)
    setIsLoading(true)
    void load()
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        clearTimer()
        invalidateRequests()
        const activeController = controllerRef.current
        activeController?.abort()
      } else if (dataRef.current === null || shouldPoll(dataRef.current, waitingTurnIdRef.current)) {
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

  return { data, isLoading, error, refresh: () => load(true) }
}
