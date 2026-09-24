import { AlertCircle, CheckCircle2, LoaderCircle, RotateCcw, Send } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { retryInterviewEvaluation, submitInterviewAnswer } from '../api/interview'
import { LAST_INTERVIEW_SESSION_KEY } from '../constants/interview'
import { PAGE_ROUTES } from '../constants/routes'
import { useInterviewResume } from '../hooks/useInterviewResume'
import { useInterviewReport } from '../hooks/useInterviewReport'
import type { InterviewSessionStatus } from '../types/interview'

const PENDING_ANSWER_PREFIX = 'interview:pending-answer:'
const TOPIC_COUNT = 5
const MAX_TURNS = 10

interface PendingAnswer {
  sessionId: string
  lastTurnId: number
  pendingRequestId: string
  answer?: string
}

function readPendingAnswer(sessionId: string): PendingAnswer | null {
  try {
    const saved = sessionStorage.getItem(PENDING_ANSWER_PREFIX + sessionId)
    if (!saved) return null
    const pending: unknown = JSON.parse(saved)
    if (typeof pending === 'object' && pending !== null
      && 'sessionId' in pending && pending.sessionId === sessionId
      && 'lastTurnId' in pending && typeof pending.lastTurnId === 'number'
      && 'pendingRequestId' in pending && typeof pending.pendingRequestId === 'string') {
      return pending as PendingAnswer
    }
  } catch { /* Storage may be unavailable. In-memory idempotency still works. */ }
  return null
}

function savePendingAnswer(pending: PendingAnswer) {
  try {
    const { sessionId, lastTurnId, pendingRequestId } = pending
    sessionStorage.setItem(PENDING_ANSWER_PREFIX + sessionId,
      JSON.stringify({ sessionId, lastTurnId, pendingRequestId }))
  } catch { /* Keep the request ID in memory if storage is unavailable. */ }
}

function clearPendingAnswer(sessionId: string) {
  try { sessionStorage.removeItem(PENDING_ANSWER_PREFIX + sessionId) }
  catch { /* Storage may be unavailable. */ }
}

export function InterviewPage() {
  const { sessionId = '' } = useParams()
  const [answer, setAnswer] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [waitingTurnId, setWaitingTurnId] = useState<number | null>(null)
  const [finishedStatus, setFinishedStatus] = useState<InterviewSessionStatus | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isRetryingEvaluation, setIsRetryingEvaluation] = useState(false)
  const requestRef = useRef<PendingAnswer | null>(null)
  const submittingRef = useRef(false)
  const activeSessionRef = useRef(sessionId)
  activeSessionRef.current = sessionId
  const lastTurnIdRef = useRef<number | null>(null)
  const { data, isLoading, error, refresh } = useInterviewResume(sessionId, waitingTurnId)

  useEffect(() => { if (sessionId) localStorage.setItem(LAST_INTERVIEW_SESSION_KEY, sessionId) }, [sessionId])
  useEffect(() => {
    setAnswer('')
    setIsSubmitting(false)
    submittingRef.current = false
    setWaitingTurnId(null)
    setFinishedStatus(null)
    setSubmitError(null)
    requestRef.current = readPendingAnswer(sessionId)
    lastTurnIdRef.current = null
  }, [sessionId])

  const currentTurn = data?.currentTurn ?? null
  const displayedStatus = finishedStatus ?? data?.status
  const isComplete = displayedStatus === 'COMPLETED'
  const isDeciding = isSubmitting || currentTurn?.status === 'DECIDING'
    || (currentTurn?.status === 'ANSWER_PENDING' && currentTurn.answer !== null)
    || (waitingTurnId !== null && currentTurn?.id === waitingTurnId)
  const canAnswer = !isComplete && !isDeciding && currentTurn?.status === 'ANSWER_PENDING'
    && currentTurn.answer === null && data?.status === 'IN_PROGRESS'
  const { data: reportState, error: reportError, refresh: refreshReport } = useInterviewReport(sessionId, isComplete)
  const evaluationStatus = reportState?.status ?? data?.evaluateStatus
  const evaluationError = reportState?.error ?? data?.evaluateError

  useEffect(() => {
    if (waitingTurnId !== null && (isComplete || (currentTurn && currentTurn.id !== waitingTurnId))) {
      setWaitingTurnId(null)
    }
  }, [currentTurn, isComplete, waitingTurnId])

  useEffect(() => {
    if (!data || data.sessionId !== sessionId) return
    const turnId = currentTurn?.id ?? null
    if (requestRef.current && (isComplete || turnId !== requestRef.current.lastTurnId)) {
      requestRef.current = null
      clearPendingAnswer(sessionId)
    }
    if (turnId !== null && lastTurnIdRef.current !== turnId) {
      lastTurnIdRef.current = turnId
      setAnswer('')
      setSubmitError(null)
    }
  }, [currentTurn, data, isComplete, sessionId])

  const submit = async () => {
    if (!currentTurn || !canAnswer || !answer.trim() || submittingRef.current) return
    submittingRef.current = true
    setIsSubmitting(true)
    setWaitingTurnId(currentTurn.id)
    setSubmitError(null)
    const turnId = currentTurn.id
    const request = requestRef.current?.sessionId === sessionId
      && requestRef.current.lastTurnId === turnId
      ? requestRef.current
      : { sessionId, lastTurnId: turnId, pendingRequestId: crypto.randomUUID(), answer: answer.trim() }
    requestRef.current = request
    savePendingAnswer(request)

    try {
      const response = await submitInterviewAnswer(sessionId, turnId,
        request.pendingRequestId, request.answer ?? answer.trim())
      if (activeSessionRef.current !== sessionId) return
      if (response.action === 'END' || response.sessionStatus === 'COMPLETED') {
        setFinishedStatus('COMPLETED')
        setWaitingTurnId(null)
      }
      switch (response.action) {
        case 'FOLLOW_UP':
        case 'NEXT_TOPIC':
        case 'END':
          setAnswer('')
          requestRef.current = null
          clearPendingAnswer(sessionId)
          break
        case 'PENDING':
          break
      }
      await refresh()
    } catch (submitRequestError) {
      if (activeSessionRef.current !== sessionId) return
      setSubmitError(submitRequestError instanceof Error ? submitRequestError.message : 'Answer submission failed.')
      const latest = await refresh()
      if (latest?.currentTurn?.id === turnId && latest.currentTurn.answer === null) {
        setWaitingTurnId(null)
      }
    } finally {
      if (activeSessionRef.current === sessionId) {
        submittingRef.current = false
        setIsSubmitting(false)
      }
    }
  }

  const retryEvaluation = async () => {
    if (isRetryingEvaluation) return
    setIsRetryingEvaluation(true)
    setSubmitError(null)
    try {
      await retryInterviewEvaluation(sessionId)
      await refreshReport()
    } catch (requestError) {
      setSubmitError(requestError instanceof Error ? requestError.message : 'Evaluation retry failed.')
    } finally {
      setIsRetryingEvaluation(false)
    }
  }

  if (isLoading && !data) return <main className="interview-page"><div className="loading-state"><LoaderCircle className="spin" size={18} /> Restoring interview...</div></main>
  if (error && !data) return <main className="interview-page"><div className="alert error-alert"><AlertCircle size={17} /> {error}</div></main>
  if (!data) return null

  return <main className="interview-page">
    <header className="interview-heading"><div><p className="eyebrow">Live interview</p><h1>Technical interview</h1><div className="interview-progress"><span>Topic {data.currentTopicOrder === null ? '--' : data.currentTopicOrder + 1} / {TOPIC_COUNT}</span><span>{data.completedTopicCount} topics completed</span><span>Turn {data.totalTurns} / {MAX_TURNS}</span></div></div><span className={`interview-status interview-status-${displayedStatus?.toLowerCase()}`}>{displayedStatus}</span></header>
    {submitError && <div className="alert error-alert"><AlertCircle size={17} /> {submitError}</div>}
    {isComplete
      ? <section className="interview-complete-card"><CheckCircle2 size={26} /><h2>面试已完成</h2><p>答案已保存</p><p>{reportError ? `评估状态查询失败：${reportError}` : evaluationStatus === 'FAILED' ? (evaluationError ?? '评估失败，报告暂不可用') : evaluationStatus === 'COMPLETED' ? `评估报告已生成，综合评分 ${reportState?.report?.overallScore ?? '--'}` : '评估报告正在生成'}</p><div className="interview-complete-actions">{evaluationStatus === 'FAILED' ? <button className="primary-button" type="button" disabled={isRetryingEvaluation} onClick={() => void retryEvaluation()}>{isRetryingEvaluation ? <><LoaderCircle className="spin" size={16} /> 重试中...</> : <><RotateCcw size={16} /> 重试评估</>}</button> : <Link className="primary-button" to={PAGE_ROUTES.interviewReport(sessionId)}>{evaluationStatus === 'COMPLETED' ? '查看报告' : '查看评估进度'}</Link>}</div></section>
      : <div className="interview-layout">
          <section className="interview-panel interview-question-panel">
            <div className="interview-question-meta"><span>Topic {data.currentTopicOrder === null ? '--' : data.currentTopicOrder + 1}</span><span>{currentTurn?.turnType ?? 'BASE'}</span></div>
            <h2>{currentTurn?.question ?? 'Waiting for the next question...'}</h2>
            {isDeciding && <div className="interview-thinking" role="status"><LoaderCircle className="spin" size={15} /> Answer saved. Analyzing your response...</div>}
            <textarea value={currentTurn?.answer ?? answer} onChange={(event) => setAnswer(event.target.value)} disabled={!canAnswer} placeholder="Write your answer..." rows={8} aria-label="Your answer" />
            <div className="interview-answer-footer"><span>{(currentTurn?.answer ?? answer).length} characters</span><button className="primary-button" type="button" disabled={!canAnswer || !answer.trim()} onClick={() => void submit()}>{isSubmitting ? <><LoaderCircle className="spin" size={16} /> Submitting...</> : <><Send size={16} /> Submit answer</>}</button></div>
          </section>
          <aside className="interview-panel interview-history-panel"><div className="section-kicker">Recent turns</div>{data.recentTurns.length === 0 ? <p className="interview-muted">Your saved answers will appear here.</p> : <div className="interview-history-list">{data.recentTurns.map((turn) => <article key={turn.id}><strong>{turn.question}</strong><p>{turn.answer ?? 'No answer saved'}</p>{turn.decision && <small>{turn.decision}</small>}</article>)}</div>}</aside>
        </div>}
    {!isDeciding && <Link className="interview-back-link" to={PAGE_ROUTES.interviewsNew}>Open another session</Link>}
  </main>
}
