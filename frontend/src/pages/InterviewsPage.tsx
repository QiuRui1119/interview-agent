import { AlertCircle, ArrowRight, FileText, LoaderCircle, Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PAGE_ROUTES } from '../constants/routes'
import { useInterviewSessions } from '../hooks/useInterviewSessions'
import type { InterviewSessionListItem } from '../types/interview'

function actionFor(item: InterviewSessionListItem) {
  if (item.status === 'ABORTED') return null
  if (item.generationStatus !== 'COMPLETED') {
    return { label: item.generationStatus === 'FAILED' ? '查看错误' : '查看进度', to: PAGE_ROUTES.interviewLoading(item.sessionId) }
  }
  if (item.status === 'IN_PROGRESS') {
    return { label: '继续面试', to: PAGE_ROUTES.interview(item.sessionId) }
  }
  if (item.status === 'COMPLETED') {
    return { label: item.evaluateStatus === 'COMPLETED' ? '查看报告' : item.evaluateStatus === 'FAILED' ? '查看错误并重试' : '查看评估状态', to: PAGE_ROUTES.interviewReport(item.sessionId) }
  }
  return null
}

function statusLabel(item: InterviewSessionListItem) {
  if (item.status === 'ABORTED') return '已终止'
  if (item.generationStatus === 'FAILED') return '生成失败'
  if (item.generationStatus !== 'COMPLETED') return '生成中'
  if (item.status === 'IN_PROGRESS') return '进行中'
  if (item.evaluateStatus === 'FAILED') return '评估失败'
  if (item.evaluateStatus === 'COMPLETED') return '已评估'
  return '评估中'
}

export function InterviewsPage() {
  const { items, isLoading, error } = useInterviewSessions()

  return <main className="interview-page">
    <header className="interview-heading"><div><p className="eyebrow">Interviews</p><h1>面试记录</h1><p className="heading-copy">继续进行中的面试，或查看已完成的评估报告。</p></div><Link className="primary-button interview-action-link" to={PAGE_ROUTES.interviewsNew}><Plus size={16} /> 新建面试</Link></header>
    {error && <div className="alert error-alert"><AlertCircle size={17} /> {error}</div>}
    {isLoading ? <div className="loading-state"><LoaderCircle className="spin" size={18} /> 正在加载面试记录...</div> : items.length === 0 ? <section className="interview-panel interview-session-empty"><FileText size={25} /><h2>暂无面试记录</h2><p>创建面试后，会话进度和评估状态会显示在这里。</p></section> : <section className="interview-session-list">{items.map((item) => {
      const action = actionFor(item)
      return <article className="interview-panel interview-session-row" key={item.sessionId}>
        <div className="interview-session-main"><span className="section-kicker">{item.skillId} · {item.difficulty}</span><h2>{item.resumeFilename ?? `Resume #${item.resumeId}`}</h2><p>{new Date(item.createdAt).toLocaleString()} · {item.sessionId}</p></div>
        <div className="interview-session-score">{item.overallScore === null ? '--' : item.overallScore}<small>score</small></div>
        <span className={`interview-status interview-status-${item.status.toLowerCase()}`}>{statusLabel(item)}</span>
        {action ? <Link className="secondary-button interview-session-open" to={action.to}>{action.label}<ArrowRight size={15} /></Link> : <span className="interview-session-disabled">不可继续</span>}
      </article>
    })}</section>}
  </main>
}
