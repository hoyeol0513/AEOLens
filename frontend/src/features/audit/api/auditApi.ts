import type { AuditResult } from '../model/audit.types'

interface ProblemDetail {
  detail?: string
  title?: string
}

export async function requestAudit(url: string, signal?: AbortSignal): Promise<AuditResult> {
  const response = await fetch('/api/v1/audits', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
    signal,
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => null) as ProblemDetail | null
    throw new Error(problem?.detail ?? problem?.title ?? `Audit failed with HTTP ${response.status}`)
  }

  return response.json() as Promise<AuditResult>
}
