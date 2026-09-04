import { useCallback, useEffect, useRef, useState } from 'react'
import {
  AlertCircle,
  ArrowUpRight,
  Check,
  FileText,
  Info,
  LoaderCircle,
  ShieldCheck,
  Sparkles,
  Upload,
  X,
} from 'lucide-react'
import { deleteResume, getResumes, uploadResume } from '../api/resume'
import { ResumeList } from '../components/ResumeList'
import type { ResumeListItem, ResumeUploadResponse } from '../types/resume'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx', 'txt', 'md']
const POLL_INTERVAL_MS = 5000

type UploadState = 'idle' | 'uploading' | 'pending' | 'processing' | 'completed' | 'failed'

interface ResumeUploadPageProps {
  onOpenDetail: (resumeId: number) => void
}

const statusCopy: Record<UploadState, string> = {
  idle: 'Ready to upload',
  uploading: 'Uploading',
  pending: 'Queued',
  processing: 'Analyzing',
  completed: 'Complete',
  failed: 'Needs attention',
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function extensionOf(fileName: string) {
  return fileName.split('.').pop()?.toLowerCase() ?? ''
}

function validateFile(file: File) {
  const extension = extensionOf(file.name)
  if (!ACCEPTED_EXTENSIONS.includes(extension)) {
    return 'Choose a PDF, DOC, DOCX, TXT, or Markdown file.'
  }
  if (file.size > MAX_FILE_SIZE) {
    return 'The selected file is larger than 10 MB.'
  }
  return null
}

function statusFromResponse(response: ResumeUploadResponse): UploadState {
  return response.resume.analysisStatus.toLowerCase() as UploadState
}

function hasResumeListChanged(previous: ResumeListItem[], next: ResumeListItem[]) {
  if (previous.length !== next.length) return true
  return next.some((item, index) => {
    const previousItem = previous[index]
    return previousItem.id !== item.id
      || previousItem.analyzeStatus !== item.analyzeStatus
      || previousItem.analyzeError !== item.analyzeError
      || previousItem.latestScore !== item.latestScore
  })
}

export function ResumeUploadPage({ onOpenDetail }: ResumeUploadPageProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [uploadState, setUploadState] = useState<UploadState>('idle')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [noticeMessage, setNoticeMessage] = useState<string | null>(null)
  const [resumeId, setResumeId] = useState<number | null>(null)
  const [uploadedResumes, setUploadedResumes] = useState<ResumeListItem[]>([])
  const [isResumeListLoading, setIsResumeListLoading] = useState(true)
  const [deletingResumeId, setDeletingResumeId] = useState<number | null>(null)

  const loadUploadedResumes = useCallback(async () => {
    try {
      const nextResumes = await getResumes()
      setUploadedResumes((previous) => (
        hasResumeListChanged(previous, nextResumes) ? nextResumes : previous
      ))

      if (resumeId) {
        const currentResume = nextResumes.find((resume) => resume.id === resumeId)
        if (currentResume) {
          setUploadState(currentResume.analyzeStatus.toLowerCase() as UploadState)
          setErrorMessage(currentResume.analyzeStatus === 'FAILED'
            ? currentResume.analyzeError ?? 'Resume analysis failed.'
            : null)
        }
      }
    } catch (error) {
      console.error('Failed to load uploaded resumes', error)
    } finally {
      setIsResumeListLoading(false)
    }
  }, [resumeId])

  const hasAnalyzing = uploadedResumes.some((resume) => (
    resume.analyzeStatus === 'PENDING' || resume.analyzeStatus === 'PROCESSING'
  ))

  useEffect(() => {
    void loadUploadedResumes()
    if (!hasAnalyzing) return

    const timer = window.setInterval(() => {
      void loadUploadedResumes()
    }, POLL_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [loadUploadedResumes, hasAnalyzing])

  const chooseFile = useCallback((file: File | undefined) => {
    if (!file) return
    const validationError = validateFile(file)
    setErrorMessage(validationError)
    setNoticeMessage(null)
    setResumeId(null)
    if (validationError) {
      setSelectedFile(null)
      setUploadState('failed')
      return
    }
    setSelectedFile(file)
    setUploadState('idle')
  }, [])

  const handleUpload = async () => {
    if (!selectedFile || uploadState === 'uploading') return
    setUploadState('uploading')
    setErrorMessage(null)
    setNoticeMessage(null)

    try {
      const response = await uploadResume(selectedFile)
      setResumeId(response.resume.id)
      setUploadState(statusFromResponse(response))
      if (response.duplicate) {
        setNoticeMessage('This resume has already been uploaded. The existing resume was found.')
      }
    } catch (error) {
      setUploadState('failed')
      setNoticeMessage(null)
      setErrorMessage(error instanceof Error ? error.message : 'Upload failed. Please try again.')
    }
  }

  const resetUpload = () => {
    setSelectedFile(null)
    setUploadState('idle')
    setErrorMessage(null)
    setNoticeMessage(null)
    setResumeId(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  const handleDeleteResume = async (id: number) => {
    const resume = uploadedResumes.find((item) => item.id === id)
    const filename = resume?.filename ?? 'this resume'
    if (!window.confirm(`Delete "${filename}" and its analysis records?`)) return

    setDeletingResumeId(id)
    setErrorMessage(null)
    try {
      await deleteResume(id)
      setUploadedResumes((previous) => previous.filter((item) => item.id !== id))
      if (resumeId === id) resetUpload()
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to delete the resume.')
    } finally {
      setDeletingResumeId(null)
    }
  }

  const currentStatus = statusCopy[uploadState]
  const isBusy = uploadState === 'uploading'

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand-lockup">
          <div className="brand-mark" aria-hidden="true"><FileText size={19} /></div>
          <div>
            <span className="brand-name">Career Lens</span>
            <span className="brand-caption">Resume workspace</span>
          </div>
        </div>
        <div className="workspace-state"><span className="live-dot" /> Analysis workspace</div>
      </header>

      <section className="page-heading">
        <div>
          <p className="eyebrow">Resume intake</p>
          <h1>Upload a resume</h1>
          <p className="heading-copy">Start a structured review of experience, skills, and project depth.</p>
        </div>
        <div className="heading-meta">
          <ShieldCheck size={17} />
          <span>Private workspace</span>
        </div>
      </section>

      <section className="workspace-grid">
        <div className="upload-tool">
          <div className="section-heading">
            <div>
              <p className="section-kicker">01 / Intake</p>
              <h2>Resume file</h2>
            </div>
            <span className="file-limit">10 MB max</span>
          </div>

          <div
            className={`dropzone${isDragging ? ' is-dragging' : ''}${selectedFile ? ' has-file' : ''}`}
            onDragOver={(event) => {
              event.preventDefault()
              setIsDragging(true)
            }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={(event) => {
              event.preventDefault()
              setIsDragging(false)
              chooseFile(event.dataTransfer.files[0])
            }}
          >
            <input
              ref={inputRef}
              className="sr-only"
              type="file"
              accept=".pdf,.doc,.docx,.txt,.md,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown"
              onChange={(event) => chooseFile(event.target.files?.[0])}
            />
            {selectedFile ? (
              <div className="selected-file">
                <div className="file-icon"><FileText size={24} /></div>
                <div className="selected-file-copy">
                  <strong>{selectedFile.name}</strong>
                  <span>{formatFileSize(selectedFile.size)} · {extensionOf(selectedFile.name).toUpperCase()}</span>
                </div>
                <button className="icon-button" type="button" aria-label="Remove selected file" title="Remove file" onClick={resetUpload}>
                  <X size={18} />
                </button>
              </div>
            ) : (
              <>
                <div className="upload-icon"><Upload size={25} /></div>
                <strong>Drop your resume here</strong>
                <span>or</span>
                <button className="secondary-button" type="button" onClick={() => inputRef.current?.click()}>
                  Browse files <ArrowUpRight size={16} />
                </button>
                <small>PDF, DOC, DOCX, TXT, or MD</small>
              </>
            )}
          </div>

          {errorMessage && (
            <div className="alert error-alert" role="alert">
              <AlertCircle size={18} />
              <span>{errorMessage}</span>
            </div>
          )}
          {noticeMessage && (
            <div className="alert notice-alert" role="status">
              <Info size={18} />
              <span>{noticeMessage}</span>
            </div>
          )}

          <div className="upload-actions">
            <div className="selection-note">
              <span className={`state-dot state-${uploadState}`} />
              <span>{currentStatus}</span>
            </div>
            <button className="primary-button" type="button" disabled={!selectedFile || isBusy} onClick={() => void handleUpload()}>
              {isBusy ? <LoaderCircle className="spin" size={18} /> : <Sparkles size={18} />}
              {isBusy ? 'Uploading...' : 'Start analysis'}
            </button>
          </div>
        </div>

        <aside className="files-panel">
          <div className="section-heading uploaded-heading">
            <div>
              <p className="section-kicker">Uploaded resumes</p>
              <h2>Uploaded files</h2>
            </div>
          </div>
          {isResumeListLoading ? <div className="compact-loading"><LoaderCircle className="spin" size={16} /> Loading files...</div> : <ResumeList resumes={uploadedResumes} compact onOpenDetail={onOpenDetail} onDelete={(id) => void handleDeleteResume(id)} deletingResumeId={deletingResumeId} />}
        </aside>
      </section>

      <footer className="page-footer">
        <span><Check size={15} /> Files are validated before upload</span>
        <span>Analysis results remain available by resume ID</span>
      </footer>
    </main>
  )
}
