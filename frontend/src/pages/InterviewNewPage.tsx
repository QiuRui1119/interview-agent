import { ArrowRight, BrainCircuit, LoaderCircle, MessageSquareText } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createInterviewSession } from '../api/interview'
import { getKnowledgeBases } from '../api/knowledgebase'
import { getResumes } from '../api/resume'
import { KnowledgeBaseSelector } from '../components/KnowledgeBaseSelector'
import { LAST_INTERVIEW_SESSION_KEY } from '../constants/interview'
import { PAGE_ROUTES } from '../constants/routes'
import type { KnowledgeBaseItem } from '../types/knowledgebase'
import type { ResumeListItem } from '../types/resume'

const FIXED_CONFIG = { skillId: 'java-backend', topicCount: 5, maxFollowUpsPerTopic: 1, maxTurns: 10 }

export function InterviewNewPage() {
  const navigate = useNavigate()
  const [resumes, setResumes] = useState<ResumeListItem[]>([])
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([])
  const [resumeId, setResumeId] = useState('')
  const [difficulty, setDifficulty] = useState('mid')
  const [selectedKnowledgeBaseIds, setSelectedKnowledgeBaseIds] = useState<number[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [validationMessage, setValidationMessage] = useState<string | null>(null)

  const completedResumes = useMemo(
    () => resumes.filter((resume) => resume.analyzeStatus === 'COMPLETED'),
    [resumes],
  )
  const selectableKnowledgeBases = useMemo(
    () => knowledgeBases.filter((item) => item.vectorStatus === 'COMPLETED' && item.enabled),
    [knowledgeBases],
  )

  useEffect(() => {
    let disposed = false
    Promise.all([getResumes(), getKnowledgeBases()])
      .then(([nextResumes, nextKnowledgeBases]) => {
        if (disposed) return
        setResumes(nextResumes)
        setKnowledgeBases(nextKnowledgeBases)
        setSelectedKnowledgeBaseIds(nextKnowledgeBases
          .filter((item) => item.sourceType === 'BUILT_IN' && item.vectorStatus === 'COMPLETED' && item.enabled)
          .map((item) => item.id))
      })
      .catch((error: unknown) => {
        if (!disposed) setErrorMessage(error instanceof Error ? error.message : 'Unable to load interview options.')
      })
      .finally(() => {
        if (!disposed) setIsLoading(false)
      })
    return () => { disposed = true }
  }, [])

  const submit = async () => {
    setValidationMessage(null)
    if (!resumeId) return setValidationMessage('Select a resume with completed analysis.')
    if (selectedKnowledgeBaseIds.length === 0) return setValidationMessage('Select at least one completed knowledge base.')

    setIsSubmitting(true)
    setErrorMessage(null)
    try {
      const result = await createInterviewSession({
        resumeId: Number(resumeId),
        difficulty,
        knowledgeBaseIds: selectedKnowledgeBaseIds,
        ...FIXED_CONFIG,
      })
      localStorage.setItem(LAST_INTERVIEW_SESSION_KEY, result.sessionId)
      navigate(PAGE_ROUTES.interviewLoading(result.sessionId))
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to create interview.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return <main className="interview-page">
    <header className="interview-heading">
      <div><p className="eyebrow">Interview workspace</p><h1>Create an interview</h1><p className="heading-copy">Choose a completed resume and the knowledge sources for your technical session.</p></div>
      <MessageSquareText size={30} className="interview-heading-icon" />
    </header>
    {isLoading ? <div className="loading-state"><LoaderCircle className="spin" size={18} /> Loading interview options...</div> : <>
      {errorMessage && <div className="alert error-alert">{errorMessage}</div>}
      {validationMessage && <div className="alert error-alert">{validationMessage}</div>}
      <section className="interview-config-grid">
        <div className="interview-panel interview-config-panel">
          <div className="interview-config-title"><div className="interview-entry-icon"><BrainCircuit size={21} /></div><div><p className="eyebrow">01 / Setup</p><h2>Interview configuration</h2></div></div>
          <label className="interview-field"><span>Resume</span><select value={resumeId} onChange={(event) => setResumeId(event.target.value)} disabled={isSubmitting}><option value="">Select a completed resume</option>{completedResumes.map((resume) => <option key={resume.id} value={resume.id}>{resume.filename}</option>)}</select></label>
          <label className="interview-field"><span>Difficulty</span><select value={difficulty} onChange={(event) => setDifficulty(event.target.value)} disabled={isSubmitting}><option value="easy">Easy</option><option value="mid">Intermediate</option><option value="hard">Hard</option></select></label>
          <div className="interview-fixed-config"><span>Topics <strong>{FIXED_CONFIG.topicCount}</strong></span><span>Follow-ups / topic <strong>{FIXED_CONFIG.maxFollowUpsPerTopic}</strong></span><span>Max turns <strong>{FIXED_CONFIG.maxTurns}</strong></span></div>
          <button className="primary-button interview-create-button" type="button" disabled={isSubmitting || isLoading || completedResumes.length === 0 || selectableKnowledgeBases.length === 0} onClick={() => void submit()}>{isSubmitting ? <><LoaderCircle className="spin" size={16} /> Creating...</> : <>Create interview <ArrowRight size={16} /></>}</button>
        </div>
        <KnowledgeBaseSelector items={knowledgeBases} selectedIds={selectedKnowledgeBaseIds} onChange={setSelectedKnowledgeBaseIds} title="Knowledge sources" eyebrow="02 / Context" />
      </section>
      {completedResumes.length === 0 && <p className="interview-muted">Upload a resume and wait for its analysis to complete before creating an interview.</p>}
      {completedResumes.length > 0 && selectableKnowledgeBases.length === 0 && <p className="interview-muted">Wait for at least one enabled knowledge base to finish vectorization before creating an interview.</p>}
    </>}
  </main>
}
