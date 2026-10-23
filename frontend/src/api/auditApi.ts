import type { Issue } from '../store/useAuditStore'

export interface AuditApiResponse {
  auditId: string
  url: string
  auditedAt: string
  scannedPages: number
  scores: {
    overall: number
    aeo: number
    a11y: number
  }
  crawl: {
    mode: 'static' | 'dynamic'
    csrLikely: boolean
    textLength: number
    semanticElements: number
    jsonLdBlocks: number
    validJsonLdBlocks: number
  }
  ai: {
    status: 'ready' | 'not-configured' | 'complete'
    score: number | null
    feedback: string
  }
  issues: Issue[]
}

interface ProblemDetail {
  detail?: string
  title?: string
}

export async function requestAudit(url: string): Promise<AuditApiResponse> {
  const response = await fetch('/api/v1/audits', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => null) as ProblemDetail | null
    throw new Error(problem?.detail ?? problem?.title ?? `Audit failed with HTTP ${response.status}`)
  }

  return response.json() as Promise<AuditApiResponse>
}
