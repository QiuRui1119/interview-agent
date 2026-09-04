import { useCallback, useEffect, useState } from 'react'
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  FileText,
  LoaderCircle,
  RefreshCw,
} from 'lucide-react'
import { getResumeAnalysis, getResumeDetail, reanalyzeResume } from '../api/resume'
import type { ResumeAnalysisResponse, ResumeDetail } from '../types/resume'

interface ResumeDetailPageProps {
  resumeId: number
  onBack: () => void
}

function formatDate(value: string | null) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('en', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function statusLabel(status: ResumeDetail['analyzeStatus']) {
  if (status === 'PENDING') return 'Queued'
  if (status === 'PROCESSING') return 'Analyzing'
  if (status === 'COMPLETED') return 'Complete'
  return 'Failed'
}

function hasDetailChanged(previous: ResumeDetail, next: ResumeDetail) {
  return previous.filename !== next.filename
    || previous.resumeText !== next.resumeText
    || previous.analyzeStatus !== next.analyzeStatus
    || previous.analyzeError !== next.analyzeError
}

export function ResumeDetailPage({ resumeId, onBack }: ResumeDetailPageProps) {
  const [resume, setResume] = useState<ResumeDetail | null>(null)
  const [analysis, setAnalysis] = useState<ResumeAnalysisResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isRetrying, setIsRetrying] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const loadResumeData = useCallback(async (silent: boolean) => {
    try {
      const nextResume = await getResumeDetail(resumeId)
      setResume((previous) => (
        previous && !hasDetailChanged(previous, nextResume) ? previous : nextResume
      ))

      if (nextResume.analyzeStatus === 'COMPLETED') {
        setAnalysis(await getResumeAnalysis(resumeId))
      } else {
        setAnalysis(null)
      }

      if (!silent) setErrorMessage(null)
    } catch (error) {
      console.error('Failed to load resume detail', error)
      if (!silent) {
        setErrorMessage(error instanceof Error ? error.message : 'Unable to load resume detail.')
      }
    } finally {
      if (!silent) setIsLoading(false)
    }
  }, [resumeId])

  useEffect(() => {
    void loadResumeData(false)
  }, [loadResumeData])

  const hasAnalyzing = resume?.analyzeStatus === 'PENDING'
    || resume?.analyzeStatus === 'PROCESSING'

  useEffect(() => {
    if (!hasAnalyzing) return

    const timer = window.setInterval(() => {
      void loadResumeData(true)
    }, 5000)

    return () => window.clearInterval(timer)
  }, [hasAnalyzing, loadResumeData])

  const handleReanalyze = async () => {
    setIsRetrying(true)
    setErrorMessage(null)
    try {
      await reanalyzeResume(resumeId)
      await loadResumeData(true)
    } catch (error) {
      console.error('Failed to reanalyze resume', error)
      setErrorMessage(error instanceof Error ? error.message : 'Unable to start analysis again.')
    } finally {
      setIsRetrying(false)
    }
  }

  if (isLoading) {
    return (
      <main className="app-shell detail-page">
        <header className="topbar"><button className="back-button" type="button" onClick={onBack}><ArrowLeft size={17} /> Back to upload</button></header>
        <div className="loading-state detail-loading"><LoaderCircle className="spin" size={20} /> Loading resume...</div>
      </main>
    )
  }

  if (!resume) {
    return (
      <main className="app-shell detail-page">
        <header className="topbar"><button className="back-button" type="button" onClick={onBack}><ArrowLeft size={17} /> Back to upload</button></header>
        <div className="page-error"><p>{errorMessage ?? 'Resume not found.'}</p></div>
      </main>
    )
  }

  const status = statusLabel(resume.analyzeStatus)

  return (
    <main className="app-shell detail-page">
      <header className="topbar">
        <button className="back-button" type="button" onClick={onBack}><ArrowLeft size={17} /> Back to upload</button>
        <div className="workspace-state"><span className="live-dot" /> Resume detail</div>
      </header>

      <section className="detail-heading">
        <div>
          <p className="eyebrow">Resume #{resume.id}</p>
          <h1>{resume.filename}</h1>
          <p className="heading-copy">Uploaded {formatDate(resume.uploadedAt)}</p>
        </div>
        <div className={`detail-status detail-status-${resume.analyzeStatus.toLowerCase()}`}>
          {resume.analyzeStatus === 'COMPLETED'
            ? <CheckCircle2 size={17} />
            : resume.analyzeStatus === 'FAILED'
              ? <AlertCircle size={17} />
              : <LoaderCircle className={hasAnalyzing ? 'spin' : ''} size={17} />}
          {status}
        </div>
      </section>

      {errorMessage && <div className="alert error-alert detail-alert" role="alert"><AlertCircle size={18} /><span>{errorMessage}</span></div>}

      <section className="detail-grid">
        <article className="document-panel">
          <div className="panel-heading">
            <div><p className="section-kicker">Resume text</p><h2>Extracted content</h2></div>
            <FileText size={19} className="panel-icon" />
          </div>
          <div className="resume-meta">
            <span>{resume.fileSize ? `${(resume.fileSize / 1024).toFixed(1)} KB` : '-'}</span>
            <span>{resume.accessCount} views</span>
            <span>Last opened {formatDate(resume.lastAccessTime)}</span>
          </div>
          <pre className="resume-text">{resume.resumeText || 'No extracted resume text available.'}</pre>
        </article>

        <article className="analysis-panel">
          <div className="panel-heading">
            <div><p className="section-kicker">Analysis</p><h2>Latest review</h2></div>
            {resume.analyzeStatus === 'FAILED' && <button className="refresh-button retry-button" type="button" disabled={isRetrying} onClick={() => void handleReanalyze()}>{isRetrying ? <LoaderCircle className="spin" size={16} /> : <RefreshCw size={16} />} Retry analysis</button>}
          </div>

          {resume.analyzeStatus === 'FAILED' ? (
            <div className="failure-state"><AlertCircle size={22} /><strong>Analysis failed</strong><p>{resume.analyzeError ?? 'The analysis worker could not finish this resume.'}</p></div>
          ) : analysis ? (
            <AnalysisDetail analysis={analysis} />
          ) : (
            <div className="analysis-waiting">
              {hasAnalyzing ? <LoaderCircle className="spin" size={22} /> : <AlertCircle size={22} />}
              <strong>{hasAnalyzing ? 'Analysis in progress' : 'Analysis result unavailable'}</strong>
              <p>{hasAnalyzing ? 'This panel will update when the worker finishes.' : 'The resume is complete, but no analysis result is available.'}</p>
            </div>
          )}
        </article>
      </section>
    </main>
  )
}

function AnalysisDetail({ analysis }: { analysis: ResumeAnalysisResponse }) {
  const scores = [
    ['Content', analysis.scoreDetail.contentScore],
    ['Structure', analysis.scoreDetail.structureScore],
    ['Skills', analysis.scoreDetail.skillMatchScore],
    ['Expression', analysis.scoreDetail.expressionScore],
    ['Projects', analysis.scoreDetail.projectScore],
  ] as const

  return (
    <div className="analysis-detail">
      <div className="detail-score"><strong>{analysis.overallScore}</strong><span>/100</span></div>
      <p className="detail-summary">{analysis.summary || 'No summary available.'}</p>
      <div className="score-grid">{scores.map(([label, score]) => <div key={label}><span>{label}</span><strong>{score}</strong></div>)}</div>
      {analysis.strengths.length > 0 && <div className="detail-list"><span className="result-label">Strengths</span>{analysis.strengths.map((strength) => <p key={strength}>{strength}</p>)}</div>}
      {analysis.suggestions.length > 0 && <div className="detail-list"><span className="result-label">Suggestions</span>{analysis.suggestions.map((suggestion) => <div className="detail-suggestion" key={`${suggestion.category}-${suggestion.issue}`}><strong>{suggestion.priority}</strong><p>{suggestion.recommendation}</p></div>)}</div>}
    </div>
  )
}
