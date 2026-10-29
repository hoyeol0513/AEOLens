import { useEffect, useState, type ReactNode } from 'react'
import { Icon } from '../../shared/ui/Icon'

export type Section = 'overview' | 'projects' | 'history' | 'api' | 'settings'

interface AppShellProps {
  activeSection: Section
  onNavigate: (section: Section) => void
  onNewAudit: () => void
  children: ReactNode
}

const navItems = [
  { section: 'overview', label: 'Overview', icon: 'grid' },
  { section: 'projects', label: 'Projects', icon: 'file' },
  { section: 'history', label: 'Audit history', icon: 'history' },
] as const

const manageItems = [
  { section: 'api', label: 'API access', icon: 'key' },
  { section: 'settings', label: 'Settings', icon: 'settings' },
] as const

export function AppShell({ activeSection, onNavigate, onNewAudit, children }: AppShellProps) {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  useEffect(() => {
    if (!mobileNavOpen) return
    const close = (event: KeyboardEvent) => event.key === 'Escape' && setMobileNavOpen(false)
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [mobileNavOpen])

  const navigate = (section: Section) => {
    onNavigate(section)
    setMobileNavOpen(false)
  }

  const sectionLabel = [...navItems, ...manageItems].find((item) => item.section === activeSection)?.label ?? 'Overview'

  return (
    <div className="app-shell">
      {mobileNavOpen && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setMobileNavOpen(false)} />}
      <aside className={`sidebar ${mobileNavOpen ? 'open' : ''}`}>
        <div className="brand"><div className="brand-mark"><span /><span /><span /></div><strong>AeoLens</strong></div>
        <button type="button" className="new-audit" onClick={onNewAudit}><Icon name="plus" size={17} /> New audit</button>
        <nav aria-label="Primary navigation">
          <span className="nav-label">Workspace</span>
          {navItems.map((item) => (
            <button key={item.section} className={activeSection === item.section ? 'active' : ''} onClick={() => navigate(item.section)}>
              <Icon name={item.icon} />{item.label}
            </button>
          ))}
          <span className="nav-label secondary">Manage</span>
          {manageItems.map((item) => (
            <button key={item.section} className={activeSection === item.section ? 'active' : ''} onClick={() => navigate(item.section)}>
              <Icon name={item.icon} />{item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="usage-card"><div><span>Guest history</span><strong>90 days</strong></div><div className="usage-track"><span /></div><small>Up to 20 recent audits</small></div>
          <div className="account-button guest-account"><span className="avatar"><Icon name="history" size={15} /></span><span><strong>Guest workspace</strong><small>Saved in this browser</small></span></div>
        </div>
      </aside>

      <main>
        <header className="topbar">
          <button className="mobile-menu" type="button" onClick={() => setMobileNavOpen(true)} aria-label="Open navigation"><Icon name="menu" /></button>
          <div className="crumbs"><span>Workspace</span><Icon name="chevron" size={14} /><strong>{sectionLabel}</strong></div>
          <div className="topbar-actions"><span className="status-chip"><i /> All systems operational</span><button type="button" aria-label="Help"><Icon name="help" /></button></div>
        </header>
        {children}
      </main>
    </div>
  )
}
