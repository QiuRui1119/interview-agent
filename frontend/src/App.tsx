import { BrowserRouter, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { ResumeDetailPage } from './pages/ResumeDetailPage'
import { ResumeUploadPage } from './pages/ResumeUploadPage'
import { KnowledgeBaseManagePage } from './pages/KnowledgeBaseManagePage'
import { KnowledgeBaseUploadPage } from './pages/KnowledgeBaseUploadPage'
import { KnowledgeBaseQueryPage } from './pages/KnowledgeBaseQueryPage'
import { AppLayout } from './components/AppLayout'
import { InterviewNewPage } from './pages/InterviewNewPage'
import { InterviewLoadingPage } from './pages/InterviewLoadingPage'
import { InterviewPage } from './pages/InterviewPage'
import { InterviewsPage } from './pages/InterviewsPage'
import { InterviewReportPage } from './pages/InterviewReportPage'
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
        <Route path={PAGE_ROUTES.interviews} element={<InterviewsPage />} />
        <Route path={PAGE_ROUTES.interviewsNew} element={<InterviewNewPage />} />
        <Route path="/interviews/:sessionId/loading" element={<InterviewLoadingPage />} />
        <Route path="/interviews/:sessionId/report" element={<InterviewReportPage />} />
        <Route path="/interviews/:sessionId" element={<InterviewPage />} />
        <Route path="*" element={<Navigate to={PAGE_ROUTES.resumes} replace />} />
      </Route>
    </Routes>
  </BrowserRouter>
}

export default App
