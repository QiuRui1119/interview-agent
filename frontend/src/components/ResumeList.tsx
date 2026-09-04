import { ChevronRight, FileText } from 'lucide-react'
import type { ResumeListItem } from '../types/resume'

interface ResumeListProps {
  resumes: ResumeListItem[]
  compact?: boolean
  onOpenDetail: (resumeId: number) => void
}

const statusLabel: Record<ResumeListItem['analyzeStatus'], string> = {
  PENDING: 'Queued',
  PROCESSING: 'Analyzing',
  COMPLETED: 'Complete',
  FAILED: 'Failed',
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value))
}

export function ResumeList({ resumes, compact = false, onOpenDetail }: ResumeListProps) {
  if (resumes.length === 0) {
    return <div className="empty-list">No uploaded resumes yet.</div>
  }

  return (
    <div className={`resume-list${compact ? ' resume-list-compact' : ''}`}>
      {resumes.map((resume) => (
        <button
          className="resume-row"
          key={resume.id}
          type="button"
          onClick={() => onOpenDetail(resume.id)}
        >
          <span className="resume-row-icon"><FileText size={18} /></span>
          <span className="resume-row-main">
            <strong>{resume.filename}</strong>
            <span>{formatDate(resume.uploadedAt)}</span>
          </span>
          <span className={`resume-status resume-status-${resume.analyzeStatus.toLowerCase()}`}>
            {statusLabel[resume.analyzeStatus]}
          </span>
          {!compact && <span className="resume-score">{resume.latestScore ?? '—'}</span>}
          <ChevronRight className="resume-row-arrow" size={17} />
        </button>
      ))}
    </div>
  )
}
