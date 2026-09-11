import { ArrowLeft, FileText, LoaderCircle, UploadCloud } from 'lucide-react'
import { useRef, useState } from 'react'
import { uploadKnowledgeBase } from '../api/knowledgebase'
import { Link, useNavigate } from 'react-router-dom'
import { PAGE_ROUTES } from '../constants/routes'

const MAX_FILE_SIZE = 10 * 1024 * 1024

export function KnowledgeBaseUploadPage() {
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()
  const [file, setFile] = useState<File | null>(null)
  const [name, setName] = useState('')
  const [category, setCategory] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const chooseFile = (next: File | undefined) => {
    if (!next) return
    const extension = next.name.split('.').pop()?.toLowerCase()
    if (!extension || !['pdf', 'doc', 'docx', 'txt', 'md', 'markdown'].includes(extension)) {
      setErrorMessage('Choose a PDF, DOC, DOCX, TXT, or Markdown file.')
      return
    }
    if (next.size > MAX_FILE_SIZE) { setErrorMessage('The selected file is larger than 10 MB.'); return }
    setFile(next); setErrorMessage(null); if (!name) setName(next.name.replace(/\.[^.]+$/, ''))
  }

  const submit = async () => {
    if (!file || !name.trim() || !category.trim()) { setErrorMessage('File, name, and category are required.'); return }
    setIsSubmitting(true); setErrorMessage(null)
    try {
      const response = await uploadKnowledgeBase(file, name.trim(), category.trim())
      navigate(PAGE_ROUTES.knowledgebase, { state: { newKnowledgeBaseId: response.id, duplicate: response.duplicate } })
    }
    catch (error) { setErrorMessage(error instanceof Error ? error.message : 'Upload failed.') }
    finally { setIsSubmitting(false) }
  }

  return <main className="kb-page"><div className="kb-heading kb-heading-narrow"><div><Link className="back-button" to={PAGE_ROUTES.knowledgebase}><ArrowLeft size={15} /> Knowledge base</Link><p className="eyebrow">Add a source</p><h1>Upload document</h1><p className="heading-copy">The file is stored first, then indexed in the background.</p></div></div>
    <section className="kb-upload-panel"><div className="section-heading"><div><p className="section-kicker">Document details</p><h2>Source metadata</h2></div><span className="file-limit">10 MB max</span></div>
      <div className="kb-form-grid"><label><span>Name</span><input value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Redis design notes" /></label><label><span>Category</span><input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="e.g. Redis" /></label></div>
      <button className={`kb-dropzone${file ? ' has-file' : ''}${isDragging ? ' is-dragging' : ''}`} type="button" onClick={() => inputRef.current?.click()} onDragOver={(event) => { event.preventDefault(); setIsDragging(true) }} onDragLeave={() => setIsDragging(false)} onDrop={(event) => { event.preventDefault(); setIsDragging(false); chooseFile(event.dataTransfer.files[0]) }}><input ref={inputRef} className="sr-only" type="file" accept=".pdf,.doc,.docx,.txt,.md,.markdown" onChange={(event) => chooseFile(event.target.files?.[0])} />{file ? <><FileText size={25} /><strong>{file.name}</strong><small>{(file.size / 1024).toFixed(1)} KB · Click to replace</small></> : <><span className="upload-icon"><UploadCloud size={25} /></span><strong>Drop a technical document here</strong><small>or choose a PDF, DOC, DOCX, TXT, or Markdown file</small></>}</button>
      {errorMessage && <div className="alert error-alert">{errorMessage}</div>}
      <div className="upload-actions"><span className="selection-note">{file ? 'Ready to queue for vectorization.' : 'Select one document to continue.'}</span><button className="primary-button" type="button" disabled={isSubmitting || !file} onClick={() => void submit()}>{isSubmitting ? <><LoaderCircle className="spin" size={16} /> Uploading...</> : 'Upload and index'}</button></div>
    </section>
  </main>
}
