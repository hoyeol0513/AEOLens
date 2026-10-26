import type { FormEvent } from 'react'
import { Icon } from '../../../shared/ui/Icon'
import { useAuditStore } from '../model/useAuditStore'

const checks = [
  { icon: 'bot', title: 'AEO readiness', description: 'Structure and machine readability' },
  { icon: 'activity', title: 'Accessibility', description: 'Semantic and inclusive markup' },
  { icon: 'target', title: 'Action plan', description: 'Issues ordered by impact' },
] as const

export function AuditStartPage() {
  const url = useAuditStore((state) => state.url)
  const urlError = useAuditStore((state) => state.urlError)
  const setUrl = useAuditStore((state) => state.setUrl)
  const runAudit = useAuditStore((state) => state.runAudit)

  const submitAudit = (event: FormEvent) => {
    event.preventDefault()
    void runAudit()
  }

  return (
    <div className="page-content audit-start-page">
      <section className="start-hero">
        <span className="eyebrow"><Icon name="sparkles" size={14} /> AI visibility, made measurable</span>
        <h1>How well can answer engines<br />understand your website?</h1>
        <p>Enter a public URL to inspect its answer-engine readiness and accessibility in one report.</p>
      </section>

      <section className="scan-panel start-scan-panel" aria-label="Run a website audit">
        <form onSubmit={submitAudit} noValidate>
          <div className={`url-input ${urlError ? 'invalid' : ''}`}>
            <Icon name="search" size={19} />
            <input
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://yourwebsite.com"
              aria-label="Website URL"
              aria-describedby={urlError ? 'url-error' : 'url-hint'}
              aria-invalid={Boolean(urlError)}
              autoFocus
            />
            {url && <button type="button" className="clear-input" onClick={() => setUrl('')} aria-label="Clear URL"><Icon name="x" size={15} /></button>}
          </div>
          <button className="analyze-button" type="submit"><Icon name="scan" size={18} />Analyze website</button>
        </form>
        {urlError
          ? <p className="form-error" id="url-error" role="alert">{urlError}</p>
          : <p className="scan-hint" id="url-hint"><Icon name="clock" size={13} /> Static analysis usually finishes within a few seconds.</p>}
      </section>

      <section className="start-checks" aria-label="Audit coverage">
        {checks.map((check) => (
          <article key={check.title}>
            <span><Icon name={check.icon} size={19} /></span>
            <div><strong>{check.title}</strong><small>{check.description}</small></div>
          </article>
        ))}
      </section>
    </div>
  )
}
