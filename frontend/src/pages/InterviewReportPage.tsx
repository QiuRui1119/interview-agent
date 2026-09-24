import { AlertCircle, ArrowLeft, CheckCircle2, LoaderCircle, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { retryInterviewEvaluation } from '../api/interview'
import { PAGE_ROUTES } from '../constants/routes'
import { useInterviewReport } from '../hooks/useInterviewReport'

export function InterviewReportPage() {
  const { sessionId = '' } = useParams()
  const { data, error, refresh } = useInterviewReport(sessionId, true)
  const [isRetrying, setIsRetrying] = useState(false)
  const [retryError, setRetryError] = useState<string | null>(null)

  const retry = async () => {
    if (isRetrying) return
    setIsRetrying(true)
    setRetryError(null)
    try {
      await retryInterviewEvaluation(sessionId)
      await refresh()
    } catch (requestError) {
      setRetryError(requestError instanceof Error ? requestError.message : 'Unable to retry evaluation.')
    } finally {
      setIsRetrying(false)
    }
  }

  if (error && !data) {
    return <main className="interview-page"><div className="alert error-alert"><AlertCircle size={17} /> {error}</div><Link className="interview-back-link" to={PAGE_ROUTES.interviews}><ArrowLeft size={14} /> Interviews</Link></main>
  }

  if (!data || data.status === 'PENDING' || data.status === 'PROCESSING') {
    return <main className="interview-page"><section className="interview-loading-card"><LoaderCircle className="spin" size={28} /><p className="eyebrow">Interview report</p><h1>评估报告正在生成</h1><p>评估完成后会自动显示报告。</p><span className="interview-progress-dot" /></section></main>
  }

  if (data.status === 'FAILED') {
    return <main className="interview-page"><header className="interview-heading"><div><p className="eyebrow">Interview report</p><h1>评估失败</h1></div></header><div className="alert error-alert"><AlertCircle size={17} /> {data.error ?? '评估报告生成失败'}</div>{retryError && <div className="alert error-alert">{retryError}</div>}<button className="primary-button interview-report-retry" type="button" disabled={isRetrying} onClick={() => void retry()}>{isRetrying ? <><LoaderCircle className="spin" size={16} /> 重试中...</> : <><RotateCcw size={16} /> 重新评估</>}</button><Link className="interview-back-link" to={PAGE_ROUTES.interviews}><ArrowLeft size={14} /> Interviews</Link></main>
  }

  const report = data.report
  if (!report) return null

  return <main className="interview-page interview-report-page">
    <header className="interview-heading"><div><p className="eyebrow">Interview report</p><h1>面试评估报告</h1><p className="heading-copy">评估时间 {new Date(report.evaluatedAt).toLocaleString()}</p></div><div className="interview-report-score"><strong>{report.overallScore}</strong><span>/ 100</span></div></header>
    <section className="interview-report-summary"><CheckCircle2 size={21} /><div><h2>总体评价</h2><p>{report.overallFeedback}</p></div></section>
    <div className="interview-report-columns">
      <section><h2>优势</h2><ul>{report.strengths.map((item) => <li key={item}>{item}</li>)}</ul></section>
      <section><h2>改进建议</h2><ul>{report.improvements.map((item) => <li key={item}>{item}</li>)}</ul></section>
    </div>
    <section className="interview-report-section"><h2>Topic 得分</h2><div className="interview-topic-scores">{report.topicScores.slice().sort((a, b) => a.topicOrder - b.topicOrder).map((topic) => <article key={topic.topicId}><div><span>Topic {topic.topicOrder + 1}</span><strong>{topic.topicName}</strong></div><b>{topic.score}</b><p>{topic.feedback}</p></article>)}</div></section>
    <section className="interview-report-section"><h2>逐轮评估</h2><div className="interview-turn-evaluations">{report.turnEvaluations.slice().sort((a, b) => a.turnOrder - b.turnOrder).map((turn) => <article className={turn.turnType === 'FOLLOW_UP' ? 'is-follow-up' : 'is-base'} key={turn.turnId}><header><span>{turn.turnType === 'FOLLOW_UP' ? '追问' : '主问题'} · 第 {turn.turnOrder + 1} 轮</span><strong>{turn.score}</strong></header><h3>{turn.question}</h3><div className="interview-report-answer"><span>回答</span><p>{turn.answer}</p></div><p className="interview-report-feedback">{turn.feedback}</p>{turn.missingPoints.length > 0 && <div className="interview-report-points"><span>待改进</span>{turn.missingPoints.map((point) => <small key={point}>{point}</small>)}</div>}</article>)}</div></section>
    <Link className="interview-back-link" to={PAGE_ROUTES.interviews}><ArrowLeft size={14} /> Interviews</Link>
  </main>
}
