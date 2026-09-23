import { ArrowRight, LoaderCircle, Plus } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { resumeInterview } from '../api/interview'
import { LAST_INTERVIEW_SESSION_KEY } from '../constants/interview'
import { PAGE_ROUTES } from '../constants/routes'

export function InterviewsPage() {
  const navigate = useNavigate()
  const [sessionId, setSessionId] = useState(() => localStorage.getItem(LAST_INTERVIEW_SESSION_KEY) ?? '')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const controllerRef = useRef<AbortController | null>(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  const openSession = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const id = sessionId.trim()
    if (!id || isLoading) return
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    setIsLoading(true)
    setError(null)
    try {
      const saved = await resumeInterview(id, controller.signal)
      if (controller.signal.aborted) return
      localStorage.setItem(LAST_INTERVIEW_SESSION_KEY, saved.sessionId)
      if (saved.status === 'CREATED' || saved.status === 'GENERATING'
        || saved.status === 'GENERATION_FAILED' || saved.status === 'ABORTED') {
        navigate(PAGE_ROUTES.interviewLoading(saved.sessionId))
      } else {
        navigate(PAGE_ROUTES.interview(saved.sessionId))
      }
    } catch (requestError) {
      if (!controller.signal.aborted) {
        setError(requestError instanceof Error ? requestError.message : 'Unable to restore interview.')
      }
    } finally {
      if (!controller.signal.aborted) setIsLoading(false)
    }
  }

  return <main className="interview-page">
    <header className="interview-heading"><div><p className="eyebrow">Interviews</p><h1>Interview sessions</h1></div></header>
    <section className="interview-session-actions">
      <Link className="primary-button interview-action-link" to={PAGE_ROUTES.interviewsNew}><Plus size={16} /> New interview</Link>
      <form className="interview-panel interview-resume-form" onSubmit={(event) => void openSession(event)}>
        <h2>Resume interview</h2>
        <label className="interview-field" htmlFor="interview-session-id"><span>Session ID</span><input id="interview-session-id" value={sessionId} onChange={(event) => setSessionId(event.target.value)} placeholder="Enter a session ID" autoComplete="off" /></label>
        {error && <div className="alert error-alert" role="alert">{error}</div>}
        <button className="primary-button" type="submit" disabled={isLoading || !sessionId.trim()}>{isLoading ? <><LoaderCircle className="spin" size={16} /> Restoring...</> : <>Continue <ArrowRight size={16} /></>}</button>
      </form>
    </section>
  </main>
}
