import { AuditLoadingPage } from './components/AuditLoadingPage'
import { AuditResultsPage } from './components/AuditResultsPage'
import { AuditStartPage } from './components/AuditStartPage'
import { useAuditStore } from './model/useAuditStore'

export function AuditPage() {
  const phase = useAuditStore((state) => state.phase)
  const result = useAuditStore((state) => state.result)

  if (phase === 'loading') return <AuditLoadingPage />
  if (phase === 'result' && result) return <AuditResultsPage result={result} />
  return <AuditStartPage />
}
