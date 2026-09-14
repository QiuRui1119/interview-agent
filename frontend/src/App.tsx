import { BrowserRouter, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { ResumeDetailPage } from './pages/ResumeDetailPage'
import { ResumeUploadPage } from './pages/ResumeUploadPage'
import { KnowledgeBaseManagePage } from './pages/KnowledgeBaseManagePage'
import { KnowledgeBaseUploadPage } from './pages/KnowledgeBaseUploadPage'
import { KnowledgeBaseQueryPage } from './pages/KnowledgeBaseQueryPage'
import { AppLayout } from './components/AppLayout'
import { PAGE_ROUTES } from './constants/routes'
import './App.css'

function ResumeDetailRoute() {
  const navigate = useNavigate()
  const { resumeId } = useParams()
  return <ResumeDetailPage resumeId={Number(resumeId)} onBack={() => navigate(PAGE_ROUTES.resumes)} />
}

function ResumeUploadRoute() {
  const navigate = useNavigate()
  return <ResumeUploadPage onOpenDetail={(id) => navigate(PAGE_ROUTES.resumeDetail(id))} />
}

function App() {
  return <BrowserRouter>
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<Navigate to={PAGE_ROUTES.resumes} replace />} />
        <Route path={PAGE_ROUTES.resumes} element={<ResumeUploadRoute />} />
        <Route path="/resumes/:resumeId" element={<ResumeDetailRoute />} />
        <Route path={PAGE_ROUTES.knowledgebase} element={<KnowledgeBaseManagePage />} />
        <Route path={PAGE_ROUTES.knowledgebaseUpload} element={<KnowledgeBaseUploadPage />} />
        <Route path={PAGE_ROUTES.knowledgebaseQuery} element={<KnowledgeBaseQueryPage />} />
        <Route path="*" element={<Navigate to={PAGE_ROUTES.resumes} replace />} />
      </Route>
    </Routes>
  </BrowserRouter>
}

export default App
