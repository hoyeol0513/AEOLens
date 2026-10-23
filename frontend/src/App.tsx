import { useEffect, useMemo, useState } from 'react'
import { useAuditStore, type Issue, type Severity } from './store/useAuditStore'
import './App.css'

type IconName =
  | 'activity' | 'alert' | 'arrow' | 'bot' | 'check' | 'chevron' | 'clock'
  | 'code' | 'copy' | 'download' | 'external' | 'file' | 'grid' | 'help'
  | 'history' | 'key' | 'menu' | 'plus' | 'scan' | 'search' | 'settings'
  | 'sparkles' | 'target' | 'x'

const paths: Record<IconName, React.ReactNode> = {
  activity: <path d="M3 12h4l2.5-7 5 14 2.5-7h4" />,
  alert: <><path d="M10.3 2.9 1.8 17a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 2.9a2 2 0 0 0-3.4 0Z" /><path d="M12 9v4M12 17h.01" /></>,
  arrow: <><path d="M5 12h14M13 6l6 6-6 6" /></>,
  bot: <><rect x="5" y="7" width="14" height="12" rx="3" /><path d="M12 3v4M8.5 12h.01M15.5 12h.01M9 16h6" /></>,
  check: <path d="m5 12 4 4L19 6" />,
  chevron: <path d="m9 18 6-6-6-6" />,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  code: <><path d="m8 9-4 3 4 3M16 9l4 3-4 3M14 5l-4 14" /></>,
  copy: <><rect x="8" y="8" width="11" height="11" rx="2" /><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" /></>,
  download: <><path d="M12 3v12M7 10l5 5 5-5M5 21h14" /></>,
  external: <><path d="M14 4h6v6M20 4l-9 9" /><path d="M18 13v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h5" /></>,
  file: <><path d="M6 3h8l4 4v14H6zM14 3v5h5M9 13h6M9 17h5" /></>,
  grid: <><rect x="4" y="4" width="6" height="6" rx="1" /><rect x="14" y="4" width="6" height="6" rx="1" /><rect x="4" y="14" width="6" height="6" rx="1" /><rect x="14" y="14" width="6" height="6" rx="1" /></>,
  help: <><circle cx="12" cy="12" r="9" /><path d="M9.8 9a2.4 2.4 0 1 1 3.4 2.2c-.8.4-1.2.9-1.2 1.8M12 17h.01" /></>,
  history: <><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5M12 7v5l3 2" /></>,
  key: <><circle cx="8" cy="15" r="4" /><path d="m11 12 8-8M16 7l2 2M14 9l2 2" /></>,
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  plus: <path d="M12 5v14M5 12h14" />,
  scan: <><path d="M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3" /><circle cx="12" cy="12" r="3" /></>,
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z" /></>,
  sparkles: <><path d="m12 3 1.3 3.7L17 8l-3.7 1.3L12 13l-1.3-3.7L7 8l3.7-1.3L12 3ZM5 14l.8 2.2L8 17l-2.2.8L5 20l-.8-2.2L2 17l2.2-.8L5 14ZM19 13l.7 1.8 1.8.7-1.8.7L19 18l-.7-1.8-1.8-.7 1.8-.7L19 13Z" /></>,
  target: <><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="4" /><path d="M12 3v2M21 12h-2M12 21v-2M3 12h2" /></>,
  x: <path d="M6 6l12 12M18 6 6 18" />,
}

function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

const severityLabels: Record<Severity, string> = { critical: 'Critical', warning: 'Warning', passed: 'Passed' }

function ScoreRing({ score, label, color, compact = false }: { score: number; label: string; color: string; compact?: boolean }) {
  return (
    <div className={`score-ring-wrap ${compact ? 'compact' : ''}`}>
      <div className="score-ring" style={{ '--score': `${score * 3.6}deg`, '--ring-color': color } as React.CSSProperties}>
        <div className="score-ring-inner"><strong>{score}</strong>{!compact && <span>/ 100</span>}</div>
      </div>
      <div className="score-label"><span>{label}</span><small>{score >= 85 ? 'Excellent' : score >= 70 ? 'Good' : 'Needs work'}</small></div>
    </div>
  )
}

function MetricCard({ icon, label, value, detail, tone }: { icon: IconName; label: string; value: string; detail: string; tone: 'violet' | 'blue' | 'green' | 'red' }) {
  return (
    <article className="metric-card">
      <div className={`metric-icon ${tone}`}><Icon name={icon} size={19} /></div>
      <div className="metric-copy"><span>{label}</span><strong>{value}</strong></div>
      <span className={`metric-detail ${tone}`}>{detail}</span>
    </article>
  )
}

function IssueRow({ issue, expanded, onToggle }: { issue: Issue; expanded: boolean; onToggle: () => void }) {
  return (
    <div className={`issue ${expanded ? 'expanded' : ''}`}>
      <button type="button" className="issue-summary" onClick={onToggle} aria-expanded={expanded}>
        <span className={`severity-icon ${issue.severity}`}><Icon name={issue.severity === 'passed' ? 'check' : 'alert'} size={16} /></span>
        <span className="issue-main">
          <span className="issue-title-line"><strong>{issue.title}</strong><span className={`severity-pill ${issue.severity}`}>{severityLabels[issue.severity]}</span></span>
          <span className="issue-description">{issue.description}</span>
        </span>
        <span className="issue-count">{issue.count} {issue.count === 1 ? 'page' : 'pages'}</span>
        <Icon name="chevron" size={17} />
      </button>
      {expanded && <div className="issue-detail"><div><span className="detail-label">Detected element</span><code>{issue.selector}</code></div><div><span className="detail-label">Recommended fix</span><p>{issue.fix}</p></div></div>}
    </div>
  )
}

function EmptySection({ title, description, icon }: { title: string; description: string; icon: IconName }) {
  const setSection = useAuditStore((state) => state.setSection)
  return <section className="empty-section"><span><Icon name={icon} size={26} /></span><h1>{title}</h1><p>{description}</p><button type="button" className="primary-button" onClick={() => setSection('overview')}>Back to overview</button></section>
}

function App() {
  const { url, setUrl, urlError, isAnalyzing, progress, progressLabel, scores, issues, aiInsight, lastAudit, scannedPages, activeSection, setSection, runAudit } = useAuditStore()
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const [activeFilter, setActiveFilter] = useState<'all' | Severity>('all')
  const [expandedIssue, setExpandedIssue] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!mobileNavOpen) return
    const close = (event: KeyboardEvent) => event.key === 'Escape' && setMobileNavOpen(false)
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [mobileNavOpen])

  const filteredIssues = useMemo(() => activeFilter === 'all' ? issues : issues.filter((issue) => issue.severity === activeFilter), [activeFilter, issues])
  const criticalCount = issues.filter((issue) => issue.severity === 'critical').length
  const highestImpactIssue = issues.find((issue) => issue.severity === 'critical') ?? issues.find((issue) => issue.severity === 'warning')
  const navigate = (section: typeof activeSection) => { setSection(section); setMobileNavOpen(false) }
  const submitAudit = (event: React.FormEvent) => { event.preventDefault(); void runAudit() }

  const exportReport = () => {
    const report = { url, auditedAt: new Date().toISOString(), scores, scannedPages, issues }
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' })
    const downloadUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = downloadUrl
    anchor.download = 'aeolens-audit-report.json'
    anchor.click()
    URL.revokeObjectURL(downloadUrl)
  }

  const copySnippet = async () => {
    await navigator.clipboard.writeText(`<article aria-labelledby="guide-title">\n  <h1 id="guide-title">Your guide title</h1>\n  <p>Lead with a concise, standalone answer.</p>\n</article>`)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1800)
  }

  return (
    <div className="app-shell">
      {mobileNavOpen && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setMobileNavOpen(false)} />}
      <aside className={`sidebar ${mobileNavOpen ? 'open' : ''}`}>
        <div className="brand"><div className="brand-mark"><span /><span /><span /></div><strong>AeoLens</strong></div>
        <button type="button" className="new-audit" onClick={() => navigate('overview')}><Icon name="plus" size={17} /> New audit</button>
        <nav aria-label="Primary navigation">
          <span className="nav-label">Workspace</span>
          <button className={activeSection === 'overview' ? 'active' : ''} onClick={() => navigate('overview')}><Icon name="grid" />Overview</button>
          <button className={activeSection === 'projects' ? 'active' : ''} onClick={() => navigate('projects')}><Icon name="file" />Projects<span className="nav-count">4</span></button>
          <button className={activeSection === 'history' ? 'active' : ''} onClick={() => navigate('history')}><Icon name="history" />Audit history</button>
          <span className="nav-label secondary">Manage</span>
          <button className={activeSection === 'api' ? 'active' : ''} onClick={() => navigate('api')}><Icon name="key" />API access</button>
          <button className={activeSection === 'settings' ? 'active' : ''} onClick={() => navigate('settings')}><Icon name="settings" />Settings</button>
        </nav>
        <div className="sidebar-bottom">
          <div className="usage-card"><div><span>Monthly audits</span><strong>7 / 20</strong></div><div className="usage-track"><span /></div><small>13 audits remaining</small></div>
          <button className="account-button" type="button"><span className="avatar">JL</span><span><strong>Jae Lee</strong><small>jae@studio.dev</small></span><span className="more">•••</span></button>
        </div>
      </aside>

      <main>
        <header className="topbar">
          <button className="mobile-menu" type="button" onClick={() => setMobileNavOpen(true)} aria-label="Open navigation"><Icon name="menu" /></button>
          <div className="crumbs"><span>Workspace</span><Icon name="chevron" size={14} /><strong>{activeSection === 'overview' ? 'Overview' : activeSection.charAt(0).toUpperCase() + activeSection.slice(1)}</strong></div>
          <div className="topbar-actions"><span className="status-chip"><i /> All systems operational</span><button type="button" aria-label="Help"><Icon name="help" /></button></div>
        </header>

        {activeSection !== 'overview' ? (
          <div className="page-content">
            {activeSection === 'projects' && <EmptySection icon="file" title="Projects are coming next" description="Group multiple domains, compare scores, and share progress with your team." />}
            {activeSection === 'history' && <EmptySection icon="history" title="Your audit history" description="Completed scans will appear here once the backend persistence layer is connected." />}
            {activeSection === 'api' && <EmptySection icon="key" title="Automate with the AeoLens API" description="API keys and CI checks will be available in the next development phase." />}
            {activeSection === 'settings' && <EmptySection icon="settings" title="Workspace settings" description="Manage team preferences, scoring thresholds, and integrations here." />}
          </div>
        ) : (
          <div className="page-content">
            <section className="page-heading">
              <div><span className="eyebrow"><Icon name="sparkles" size={14} /> AI visibility, made measurable</span><h1>Website health at a glance.</h1><p>See how answer engines understand your content—and where accessibility gets in the way.</p></div>
              <button type="button" className="export-button" onClick={exportReport}><Icon name="download" size={17} /> Export report</button>
            </section>

            <section className="scan-panel" aria-label="Run a website audit">
              <form onSubmit={submitAudit} noValidate>
                <div className={`url-input ${urlError ? 'invalid' : ''}`}><Icon name="search" size={19} /><input value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://yourwebsite.com" aria-label="Website URL" aria-invalid={Boolean(urlError)} />{url && !isAnalyzing && <button type="button" className="clear-input" onClick={() => setUrl('')} aria-label="Clear URL"><Icon name="x" size={15} /></button>}</div>
                <button className="analyze-button" type="submit" disabled={isAnalyzing}>{isAnalyzing ? <><span className="spinner" />Analyzing…</> : <><Icon name="scan" size={18} />Analyze website</>}</button>
              </form>
              {urlError ? <p className="form-error">{urlError}</p> : <p className="scan-hint"><span><Icon name="clock" size={13} /> Last audit {lastAudit}</span><span>•</span><span>{scannedPages} pages scanned</span></p>}
              {isAnalyzing && <div className="analysis-progress" aria-live="polite"><div><span>{progressLabel}</span><strong>{progress}%</strong></div><div className="progress-track"><span style={{ width: `${progress}%` }} /></div><div className="analysis-skeleton" aria-hidden="true"><span /><span /><span /></div></div>}
            </section>

            <section className="metrics-grid" aria-label="Audit score summary">
              <MetricCard icon="target" label="Overall score" value={`${scores.overall}`} detail="+6 pts" tone="violet" />
              <MetricCard icon="bot" label="AEO score" value={`${scores.aeo}`} detail="Good" tone="blue" />
              <MetricCard icon="activity" label="Accessibility" value={`${scores.a11y}`} detail="+9 pts" tone="green" />
              <MetricCard icon="alert" label="Critical issues" value={`${criticalCount}`} detail="Needs action" tone="red" />
            </section>

            <div className="dashboard-grid">
              <section className="card score-card">
                <div className="card-heading"><div><span className="section-kicker">PERFORMANCE</span><h2>Answer engine readiness</h2></div><span className="trend-positive">↗ 8.2%</span></div>
                <div className="score-layout">
                  <ScoreRing score={scores.overall} label="Overall health" color="#9b87f5" />
                  <div className="score-breakdown">
                    <div className="bar-row"><div><span>Content structure</span><strong>84</strong></div><div className="bar"><span style={{ width: '84%', background: '#9b87f5' }} /></div></div>
                    <div className="bar-row"><div><span>Machine readability</span><strong>76</strong></div><div className="bar"><span style={{ width: '76%', background: '#63a5fa' }} /></div></div>
                    <div className="bar-row"><div><span>Semantic clarity</span><strong>91</strong></div><div className="bar"><span style={{ width: '91%', background: '#4fd1a1' }} /></div></div>
                    <div className="bar-row"><div><span>Schema coverage</span><strong>68</strong></div><div className="bar"><span style={{ width: '68%', background: '#f5a75b' }} /></div></div>
                  </div>
                </div>
                <div className="mini-scores"><ScoreRing score={scores.aeo} label="AEO" color="#63a5fa" compact /><ScoreRing score={scores.a11y} label="Accessibility" color="#4fd1a1" compact /></div>
              </section>

              <section className="card ai-card">
                <div className="ai-glow" />
                <div className="ai-card-top"><span><Icon name="sparkles" size={17} /></span><small>AI INSIGHT</small></div>
                <h2>{aiInsight.status === 'not-configured' ? <>Static diagnosis is ready.<br />AI context is waiting.</> : <>Your content is clear.<br />Your structure can work harder.</>}</h2>
                <p>{aiInsight.feedback}</p>
                <div className="priority-fix"><span>Highest-impact fix</span><strong>{highestImpactIssue?.title ?? 'No priority issue found'}</strong><small>{highestImpactIssue ? highestImpactIssue.fix : 'Keep monitoring the page as it changes.'}</small></div>
                <button type="button" className="ai-action" onClick={() => { setActiveFilter('critical'); document.getElementById('issues')?.scrollIntoView({ behavior: 'smooth' }) }}>View action plan <Icon name="arrow" size={17} /></button>
              </section>
            </div>

            <section className="card issues-card" id="issues">
              <div className="issues-header">
                <div><span className="section-kicker">DIAGNOSTICS</span><h2>Issues & opportunities</h2><p>Prioritized by impact on discoverability and usability.</p></div>
                <div className="filter-tabs" role="tablist" aria-label="Issue filters">
                  {(['all', 'critical', 'warning', 'passed'] as const).map((filter) => <button key={filter} role="tab" aria-selected={activeFilter === filter} className={activeFilter === filter ? 'active' : ''} onClick={() => setActiveFilter(filter)}>{filter === 'all' ? 'All issues' : severityLabels[filter]}<span>{filter === 'all' ? issues.length : issues.filter((issue) => issue.severity === filter).length}</span></button>)}
                </div>
              </div>
              <div className="issue-list">{filteredIssues.map((issue) => <IssueRow key={issue.id} issue={issue} expanded={expandedIssue === issue.id} onToggle={() => setExpandedIssue(expandedIssue === issue.id ? null : issue.id)} />)}</div>
            </section>

            <section className="card snippet-card">
              <div className="snippet-copy"><span className="section-kicker"><Icon name="code" size={14} /> READY-TO-USE FIX</span><h2>Make your primary answer explicit</h2><p>A semantic article wrapper and a labelled heading give crawlers and assistive technology the same reliable entry point.</p><div className="impact-row"><span><Icon name="bot" size={15} /> AEO +4</span><span><Icon name="activity" size={15} /> A11y +3</span></div></div>
              <div className="code-window"><div className="code-top"><span>React · JSX</span><button type="button" onClick={() => void copySnippet()}><Icon name={copied ? 'check' : 'copy'} size={15} />{copied ? 'Copied' : 'Copy'}</button></div><pre><code><span className="code-muted">{'<article '}</span><span className="code-key">aria-labelledby</span><span className="code-muted">={'"guide-title"'}{'>'}</span>{'\n  '}<span className="code-muted">{'<h1 '}</span><span className="code-key">id</span><span className="code-muted">={'"guide-title"'}{'>'}</span>{'\n    '}Your guide title{'\n  '}<span className="code-muted">{'</h1>'}</span>{'\n  '}<span className="code-muted">{'<p>'}</span>Lead with a concise answer.<span className="code-muted">{'</p>'}</span>{'\n'}<span className="code-muted">{'</article>'}</span></code></pre></div>
            </section>

            <footer><span>AeoLens analysis engine · v0.1</span><a href="https://www.w3.org/WAI/standards-guidelines/wcag/" target="_blank" rel="noreferrer">Scoring methodology <Icon name="external" size={13} /></a></footer>
          </div>
        )}
      </main>
    </div>
  )
}

export default App
