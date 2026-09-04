import { useCallback, useEffect, useState } from 'react'
import { ResumeDetailPage } from './pages/ResumeDetailPage'
import { ResumeUploadPage } from './pages/ResumeUploadPage'
import './App.css'

function App() {
  const [path, setPath] = useState(window.location.pathname)

  const navigate = useCallback((nextPath: string) => {
    window.history.pushState({}, '', nextPath)
    setPath(nextPath)
  }, [])

  useEffect(() => {
    const handlePopState = () => setPath(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const detailMatch = path.match(/^\/resumes\/(\d+)$/)
  if (detailMatch) {
    return <ResumeDetailPage resumeId={Number(detailMatch[1])} onBack={() => navigate('/')} />
  }

  return (
    <ResumeUploadPage
      onOpenDetail={(id) => navigate(`/resumes/${id}`)}
    />
  )
}

export default App
