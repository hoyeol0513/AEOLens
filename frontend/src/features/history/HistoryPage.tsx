import { useEffect, useState } from 'react'
import { Icon } from '../../shared/ui/Icon'
import type { AuditResult, AuditSummary } from '../audit/model/audit.types'
import { deleteSavedAudit, requestAuditHistory, requestSavedAudit } from './historyApi'

interface HistoryPageProps {
  onOpenAudit: (result: AuditResult) => void
  onNewAudit: () => void
}

function scoreTone(score: number) {
  if (score >= 85) return 'excellent'
  if (score >= 70) return 'good'
  return 'needs-work'
}

export function HistoryPage({ onOpenAudit, onNewAudit }: HistoryPageProps) {
  const [entries, setEntries] = useState<AuditSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [busyAuditId, setBusyAuditId] = useState<string | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    requestAuditHistory(controller.signal)
      .then(setEntries)
      .catch((reason) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Could not load audit history.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  const openAudit = async (auditId: string) => {
    setBusyAuditId(auditId)
    setError('')
    try {
      onOpenAudit(await requestSavedAudit(auditId))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not open the saved report.')
    } finally {
      setBusyAuditId(null)
    }
  }

  const removeAudit = async (entry: AuditSummary) => {
    if (!window.confirm(`Delete the saved audit for ${entry.url}?`)) return
    setBusyAuditId(entry.auditId)
    setError('')
    try {
      await deleteSavedAudit(entry.auditId)
      setEntries((current) => current.filter((item) => item.auditId !== entry.auditId))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not delete the saved report.')
    } finally {
      setBusyAuditId(null)
    }
  }

  return (
    <div className="page-content history-page">
      <section className="page-heading history-heading">
        <div>
          <span className="eyebrow"><Icon name="history" size={14} /> Anonymous history</span>
          <h1>Your recent audits.</h1>
          <p>Saved to this browser's guest workspace for 90 days. Clearing the guest cookie disconnects this history.</p>
        </div>
        <button type="button" className="primary-button history-new-button" onClick={onNewAudit}><Icon name="plus" size={16} /> New audit</button>
      </section>

      {error && <div className="history-error" role="alert"><Icon name="alert" size={16} /> {error}</div>}

      {loading ? (
        <section className="card history-state"><span className="history-spinner" /><p>Loading saved audits…</p></section>
      ) : entries.length === 0 ? (
        <section className="card history-state history-empty">
          <span><Icon name="history" size={25} /></span>
          <h2>No saved audits yet</h2>
          <p>Completed audits will appear here automatically—no account required.</p>
          <button type="button" className="primary-button" onClick={onNewAudit}>Run your first audit</button>
        </section>
      ) : (
        <section className="card history-list" aria-label="Saved audits">
          <div className="history-list-head"><span>Target</span><span>Scores</span><span>Issues</span><span>Audited</span><span /></div>
          {entries.map((entry) => {
            const auditedAt = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(entry.auditedAt))
            const hostname = (() => { try { return new URL(entry.url).hostname } catch { return entry.url } })()
            const busy = busyAuditId === entry.auditId
            return (
              <article className="history-row" key={entry.auditId}>
                <button className="history-target" type="button" disabled={busy} onClick={() => openAudit(entry.auditId)}>
                  <span><Icon name="scan" size={17} /></span>
                  <span><strong>{hostname}</strong><small>{entry.url}</small></span>
                </button>
                <div className="history-scores">
                  <strong className={scoreTone(entry.scores.overall)}>{entry.scores.overall}</strong>
                  <span>AEO {entry.scores.aeo}</span><span>A11y {entry.scores.a11y}</span>
                </div>
                <div className="history-issues"><strong>{entry.issueCount}</strong><span>{entry.crawlMode}</span></div>
                <time dateTime={entry.auditedAt}>{auditedAt}</time>
                <div className="history-actions">
                  <button type="button" disabled={busy} onClick={() => openAudit(entry.auditId)}>{busy ? 'Loading…' : 'Open'}</button>
                  <button type="button" className="delete" disabled={busy} onClick={() => removeAudit(entry)} aria-label={`Delete audit for ${hostname}`}><Icon name="x" size={15} /></button>
                </div>
              </article>
            )
          })}
        </section>
      )}
    </div>
  )
}
