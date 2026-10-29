export type Severity = 'critical' | 'warning' | 'passed'
export type AuditPhase = 'input' | 'loading' | 'result'

export interface Issue {
  id: string
  severity: Severity
  title: string
  description: string
  count: number
  selector: string
  fix: string
}

export interface AuditResult {
  auditId: string
  url: string
  auditedAt: string
  scannedPages: number
  scores: { overall: number; aeo: number; a11y: number }
  crawl: {
    mode: 'static' | 'dynamic'
    csrLikely: boolean
    textLength: number
    semanticElements: number
    jsonLdBlocks: number
    validJsonLdBlocks: number
  }
  ai: {
    status: 'ready' | 'not-configured' | 'complete' | 'unavailable'
    score: number | null
    feedback: string
  }
  issues: Issue[]
}

export interface AuditSummary {
  auditId: string
  url: string
  auditedAt: string
  scores: { overall: number; aeo: number; a11y: number }
  crawlMode: 'static' | 'dynamic'
  csrLikely: boolean
  issueCount: number
}
