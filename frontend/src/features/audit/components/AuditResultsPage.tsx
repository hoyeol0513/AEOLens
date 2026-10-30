import { useState } from 'react'
import { Icon } from '../../../shared/ui/Icon'
import type { AuditResult } from '../model/audit.types'
import { FixSnippet } from './FixSnippet'
import { IssuesPanel, type IssueFilter } from './IssuesPanel'
import { ResultOverview } from './ResultOverview'

export function AuditResultsPage({ result }: { result: AuditResult }) {
  const [issueFilter, setIssueFilter] = useState<IssueFilter>('all')

  const exportReport = () => {
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: 'application/json' })
    const downloadUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = downloadUrl
    anchor.download = `aeolens-${result.auditId}.json`
    anchor.click()
    URL.revokeObjectURL(downloadUrl)
  }

  const auditedAt = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(result.auditedAt))

  return (
    <div className="page-content">
      <section className="page-heading result-heading">
        <div>
          <span className="eyebrow"><Icon name="check" size={14} /> Audit complete</span>
          <h1>Website health at a glance.</h1>
          <p><span>{result.url}</span> · {result.scannedPages} pages · {auditedAt}</p>
        </div>
        <button type="button" className="export-button" onClick={exportReport}><Icon name="download" size={17} /> Export report</button>
      </section>

      <ResultOverview result={result} onShowIssues={setIssueFilter} />
      <IssuesPanel issues={result.issues} summary={result.issueSummary} activeFilter={issueFilter} onFilterChange={setIssueFilter} />
      <FixSnippet />

      <footer><span>AeoLens analysis engine · v0.1</span><a href="https://www.w3.org/WAI/standards-guidelines/wcag/" target="_blank" rel="noreferrer">Scoring methodology <Icon name="external" size={13} /></a></footer>
    </div>
  )
}
