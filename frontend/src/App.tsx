import { useState } from 'react'
import { AppShell, type Section } from './components/layout/AppShell'
import { AuditPage } from './features/audit/AuditPage'
import { useAuditStore } from './features/audit/model/useAuditStore'
import type { AuditResult } from './features/audit/model/audit.types'
import { HistoryPage } from './features/history/HistoryPage'
import { PlaceholderPage } from './pages/PlaceholderPage'
import './App.css'

function App() {
  const [activeSection, setActiveSection] = useState<Section>('overview')
  const resetAudit = useAuditStore((state) => state.resetAudit)
  const showResult = useAuditStore((state) => state.showResult)

  const startNewAudit = () => {
    resetAudit()
    setActiveSection('overview')
  }

  const openSavedAudit = (result: AuditResult) => {
    showResult(result)
    setActiveSection('overview')
  }

  return (
    <AppShell activeSection={activeSection} onNavigate={setActiveSection} onNewAudit={startNewAudit}>
      {activeSection === 'overview' && <AuditPage />}
      {activeSection === 'history' && <HistoryPage onOpenAudit={openSavedAudit} onNewAudit={startNewAudit} />}
      {activeSection !== 'overview' && activeSection !== 'history'
        && <PlaceholderPage section={activeSection} onBack={() => setActiveSection('overview')} />}
    </AppShell>
  )
}

export default App
