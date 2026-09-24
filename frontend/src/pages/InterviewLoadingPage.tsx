import { AlertCircle, LoaderCircle } from 'lucide-react'
import { useEffect } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PAGE_ROUTES } from '../constants/routes'
import { useInterviewResume } from '../hooks/useInterviewResume'

export function InterviewLoadingPage() {
  const { sessionId = '' } = useParams()
  const navigate = useNavigate()
  const { data, isLoading, error } = useInterviewResume(sessionId)

  useEffect(() => {
    if (data && data.generationStatus === 'COMPLETED' && data.currentTurn
      && data.status !== 'ABORTED') {
      navigate(PAGE_ROUTES.interview(sessionId), { replace: true })
    }
  }, [data, navigate, sessionId])

  if (data?.generationStatus === 'FAILED' || data?.status === 'ABORTED') {
    return <main className="interview-page"><div className="interview-state error-alert"><AlertCircle size={18} /><span>{data.status === 'ABORTED' ? 'This interview was aborted.' : 'Interview generation failed.'}</span></div><Link className="secondary-button" to={PAGE_ROUTES.interviewsNew}>Back</Link></main>
  }

  if (data?.generationStatus === 'COMPLETED' && !data.currentTurn) {
    return <main className="interview-page"><div className="interview-state error-alert"><AlertCircle size={18} /><span>Interview generation completed, but no question is available.</span></div><Link className="secondary-button" to={PAGE_ROUTES.interviewsNew}>Back</Link></main>
  }

  return <main className="interview-page"><section className="interview-loading-card"><LoaderCircle className="spin" size={28} /><p className="eyebrow">Preparing interview</p><h1>Building your questions</h1><p>{error ?? (isLoading ? 'Loading the saved session...' : 'The interview will open when the first question is ready.')}</p><span className="interview-progress-dot" /></section></main>
}
