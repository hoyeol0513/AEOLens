import type { AuditResult, AuditSummary } from '../audit/model/audit.types'

interface ProblemDetail {
  detail?: string
  title?: string
}

async function ensureSuccess(response: Response) {
  if (response.ok) return
  const problem = await response.json().catch(() => null) as ProblemDetail | null
  throw new Error(problem?.detail ?? problem?.title ?? `Request failed with HTTP ${response.status}`)
}

export async function requestAuditHistory(signal?: AbortSignal): Promise<AuditSummary[]> {
  const response = await fetch('/api/v1/audits', { credentials: 'include', signal })
  await ensureSuccess(response)
  return response.json() as Promise<AuditSummary[]>
}

export async function requestSavedAudit(auditId: string): Promise<AuditResult> {
  const response = await fetch(`/api/v1/audits/${auditId}`, { credentials: 'include' })
  await ensureSuccess(response)
  return response.json() as Promise<AuditResult>
}

export async function deleteSavedAudit(auditId: string): Promise<void> {
  const response = await fetch(`/api/v1/audits/${auditId}`, {
    method: 'DELETE',
    credentials: 'include',
  })
  await ensureSuccess(response)
}
