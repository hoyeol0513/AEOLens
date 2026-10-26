import { Icon } from '../../../shared/ui/Icon'
import { useAuditStore } from '../model/useAuditStore'

const stages = [
  { threshold: 12, label: 'Fetch website' },
  { threshold: 38, label: 'Map structure' },
  { threshold: 64, label: 'Check accessibility' },
  { threshold: 86, label: 'Build report' },
]

export function AuditLoadingPage() {
  const url = useAuditStore((state) => state.url)
  const progress = useAuditStore((state) => state.progress)
  const progressLabel = useAuditStore((state) => state.progressLabel)

  return (
    <div className="page-content audit-loading-page" aria-live="polite" aria-busy="true">
      <section className="loading-card">
        <div className="loading-orbit"><span><Icon name="scan" size={28} /></span></div>
        <span className="eyebrow">LIVE WEBSITE ANALYSIS</span>
        <h1>Reading your website.</h1>
        <p className="loading-url">{url}</p>

        <div className="loading-progress">
          <div><span>{progressLabel}</span><strong>{progress}%</strong></div>
          <div className="progress-track"><span style={{ width: `${progress}%` }} /></div>
        </div>

        <ol className="loading-stages">
          {stages.map((stage) => {
            const complete = progress > stage.threshold
            const active = progress >= stage.threshold && !complete
            return (
              <li key={stage.label} className={`${complete ? 'complete' : ''} ${active ? 'active' : ''}`}>
                <span>{complete ? <Icon name="check" size={13} /> : null}</span>{stage.label}
              </li>
            )
          })}
        </ol>
      </section>
    </div>
  )
}
