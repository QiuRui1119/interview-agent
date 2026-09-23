import { BookOpen, FileText, LayoutDashboard, MessageSquareText, Menu, Mic2, Plus, RotateCcw, X } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { LAST_INTERVIEW_SESSION_KEY } from '../constants/interview'
import { PAGE_ROUTES } from '../constants/routes'

export function AppLayout() {
  const [isOpen, setIsOpen] = useState(false)
  const { pathname } = useLocation()
  const lastSessionId = localStorage.getItem(LAST_INTERVIEW_SESSION_KEY)?.trim()
  const closeMenu = () => setIsOpen(false)

  return <div className={`app-layout${isOpen ? ' is-nav-open' : ''}`}>
    <header className="app-layout-header">
      <div className="brand-lockup">
        <span className="brand-mark"><LayoutDashboard size={18} /></span>
        <span><strong className="brand-name">Interview agent</strong><span className="brand-caption">Knowledge workspace</span></span>
      </div>
      <button className="app-menu-button" type="button" aria-label={isOpen ? 'Close navigation' : 'Open navigation'} onClick={() => setIsOpen((open) => !open)}>{isOpen ? <X size={19} /> : <Menu size={19} />}</button>
    </header>
    <aside className="app-layout-nav" aria-label="Primary navigation">
      <nav className="app-nav">
        <NavLink to={PAGE_ROUTES.resumes} end className={({ isActive }) => isActive ? 'app-nav-link is-active' : 'app-nav-link'} onClick={closeMenu}><FileText size={16} /> Resumes</NavLink>
        <NavLink to={PAGE_ROUTES.knowledgebase} className={({ isActive }) => isActive ? 'app-nav-link is-active' : 'app-nav-link'} onClick={closeMenu}><BookOpen size={16} /> Knowledge base</NavLink>
        <NavLink to={PAGE_ROUTES.knowledgebaseQuery} className={({ isActive }) => isActive ? 'app-nav-link is-active' : 'app-nav-link'} onClick={closeMenu}><MessageSquareText size={16} /> Ask knowledge</NavLink>
        <NavLink to={PAGE_ROUTES.interviews} end className={({ isActive }) => isActive ? 'app-nav-link is-active' : 'app-nav-link'} onClick={closeMenu}><Mic2 size={16} /> Interviews</NavLink>
        <NavLink to={PAGE_ROUTES.interviewsNew} className={({ isActive }) => isActive ? 'app-nav-link is-active' : 'app-nav-link'} onClick={closeMenu}><Plus size={16} /> New interview</NavLink>
        <NavLink to={lastSessionId ? PAGE_ROUTES.interview(lastSessionId) : PAGE_ROUTES.interviews} className={({ isActive }) => isActive && pathname !== PAGE_ROUTES.interviews ? 'app-nav-link is-active' : 'app-nav-link'} onClick={closeMenu}><RotateCcw size={16} /> Resume interview</NavLink>
      </nav>
    </aside>
    <button className="app-nav-scrim" type="button" aria-label="Close navigation" onClick={closeMenu} />
    <main className="app-layout-content"><Outlet /></main>
  </div>
}
