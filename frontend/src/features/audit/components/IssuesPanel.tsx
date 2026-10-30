import { useMemo, useState } from 'react'
import { Icon } from '../../../shared/ui/Icon'
import type { AuditResult, Issue, Severity } from '../model/audit.types'

const severityLabels: Record<Severity, string> = { critical: 'Critical', warning: 'Warning', passed: 'Passed' }

function IssueRow({ issue, expanded, onToggle }: { issue: Issue; expanded: boolean; onToggle: () => void }) {
  return (
    <div className={`issue ${expanded ? 'expanded' : ''}`}>
      <button type="button" className="issue-summary" onClick={onToggle} aria-expanded={expanded}>
        <span className={`severity-icon ${issue.severity}`}><Icon name={issue.severity === 'passed' ? 'check' : 'alert'} size={16} /></span>
        <span className="issue-main">
          <span className="issue-title-line"><strong>{issue.title}</strong><span className={`severity-pill ${issue.severity}`}>{severityLabels[issue.severity]}</span></span>
          <span className="issue-description">{issue.description}</span>
        </span>
        <span className="issue-count">{issue.severity === 'passed' ? 'Check passed' : `${issue.count} ${issue.count === 1 ? 'occurrence' : 'occurrences'}`}</span>
        <Icon name="chevron" size={17} />
      </button>
      {expanded && <div className="issue-detail"><div><span className="detail-label">Detected element</span><code>{issue.selector}</code></div><div><span className="detail-label">Recommended fix</span><p>{issue.fix}</p></div></div>}
    </div>
  )
}

export type IssueFilter = 'all' | Severity

interface IssuesPanelProps {
  issues: Issue[]
  summary: AuditResult['issueSummary']
  activeFilter: IssueFilter
  onFilterChange: (filter: IssueFilter) => void
}

export function IssuesPanel({ issues, summary, activeFilter, onFilterChange }: IssuesPanelProps) {
  const [expandedIssue, setExpandedIssue] = useState<string | null>(null)
  const filteredIssues = useMemo(() => activeFilter === 'all' ? issues : issues.filter((issue) => issue.severity === activeFilter), [activeFilter, issues])

  return (
    <section className="card issues-card" id="issues">
      <div className="issues-header">
        <div><span className="section-kicker">DIAGNOSTICS</span><h2>Checks & opportunities</h2><p>Prioritized by impact on discoverability and usability.</p></div>
        <div className="filter-tabs" role="tablist" aria-label="Issue filters">
          {(['all', 'critical', 'warning', 'passed'] as const).map((filter) => (
            <button key={filter} type="button" role="tab" aria-selected={activeFilter === filter} className={activeFilter === filter ? 'active' : ''} onClick={() => onFilterChange(filter)}>
              {filter === 'all' ? 'All checks' : severityLabels[filter]}
              <span>{filter === 'all' ? summary.total : summary[filter]}</span>
            </button>
          ))}
        </div>
      </div>
      <div className="issue-list">
        {filteredIssues.length
          ? filteredIssues.map((issue) => <IssueRow key={issue.id} issue={issue} expanded={expandedIssue === issue.id} onToggle={() => setExpandedIssue(expandedIssue === issue.id ? null : issue.id)} />)
          : <p className="issues-empty">No issues match this filter.</p>}
      </div>
    </section>
  )
}
