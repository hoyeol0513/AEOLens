import { Icon } from '../../../shared/ui/Icon'
import type { AuditResult } from '../model/audit.types'
import { MetricCard } from './MetricCard'
import { ScoreRing } from './ScoreRing'

function scoreLabel(score: number) {
  if (score >= 85) return 'Excellent'
  if (score >= 70) return 'Good'
  return 'Needs work'
}

interface ResultOverviewProps {
  result: AuditResult
  onShowIssues: (severity: 'critical' | 'warning') => void
}

export function ResultOverview({ result, onShowIssues }: ResultOverviewProps) {
  const { scores, issues, ai, crawl } = result
  const criticalCount = issues.filter((issue) => issue.severity === 'critical').length
  const highestImpactIssue = issues.find((issue) => issue.severity === 'critical') ?? issues.find((issue) => issue.severity === 'warning')

  const showPriorityIssues = () => {
    onShowIssues(highestImpactIssue?.severity === 'warning' ? 'warning' : 'critical')
    document.getElementById('issues')?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <>
      <section className="metrics-grid" aria-label="Audit score summary">
        <MetricCard icon="target" label="Overall score" value={`${scores.overall}`} detail={scoreLabel(scores.overall)} tone="violet" />
        <MetricCard icon="bot" label="AEO score" value={`${scores.aeo}`} detail={scoreLabel(scores.aeo)} tone="blue" />
        <MetricCard icon="activity" label="Accessibility" value={`${scores.a11y}`} detail={scoreLabel(scores.a11y)} tone="green" />
        <MetricCard icon="alert" label="Critical issues" value={`${criticalCount}`} detail={criticalCount ? 'Needs action' : 'All clear'} tone="red" />
      </section>

      <div className="dashboard-grid">
        <section className="card score-card">
          <div className="card-heading"><div><span className="section-kicker">PERFORMANCE</span><h2>Answer engine readiness</h2></div></div>
          <div className="score-layout">
            <ScoreRing score={scores.overall} label="Overall health" color="#9b87f5" />
            <dl className="result-facts">
              <div><dt>Crawl mode</dt><dd>{crawl.mode}</dd></div>
              <div><dt>Semantic elements</dt><dd>{crawl.semanticElements}</dd></div>
              <div><dt>Valid JSON-LD</dt><dd>{crawl.validJsonLdBlocks} / {crawl.jsonLdBlocks}</dd></div>
              <div><dt>Client-rendered</dt><dd>{crawl.csrLikely ? 'Likely' : 'No signal'}</dd></div>
            </dl>
          </div>
          <div className="mini-scores"><ScoreRing score={scores.aeo} label="AEO" color="#63a5fa" compact /><ScoreRing score={scores.a11y} label="Accessibility" color="#4fd1a1" compact /></div>
        </section>

        <section className="card ai-card">
          <div className="ai-glow" />
          <div className="ai-card-top"><span><Icon name="sparkles" size={17} /></span><small>AI INSIGHT</small></div>
          <h2>{ai.status === 'not-configured' ? <>Static diagnosis is ready.<br />AI context is waiting.</> : <>Your analysis is ready.<br />Start with the biggest impact.</>}</h2>
          <p>{ai.feedback}</p>
          <div className="priority-fix"><span>Highest-impact fix</span><strong>{highestImpactIssue?.title ?? 'No priority issue found'}</strong><small>{highestImpactIssue?.fix ?? 'Keep monitoring the page as it changes.'}</small></div>
          {highestImpactIssue && <button type="button" className="ai-action" onClick={showPriorityIssues}>View action plan <Icon name="arrow" size={17} /></button>}
        </section>
      </div>
    </>
  )
}
