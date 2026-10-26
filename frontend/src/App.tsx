import { useState } from 'react'
import { AppShell, type Section } from './components/layout/AppShell'
import { AuditPage } from './features/audit/AuditPage'
import { useAuditStore } from './features/audit/model/useAuditStore'
import { PlaceholderPage } from './pages/PlaceholderPage'
import './App.css'

function App() {
  const [activeSection, setActiveSection] = useState<Section>('overview')
  const resetAudit = useAuditStore((state) => state.resetAudit)

  const startNewAudit = () => {
    resetAudit()
    setActiveSection('overview')
  }

  return (
    <AppShell activeSection={activeSection} onNavigate={setActiveSection} onNewAudit={startNewAudit}>
      {activeSection === 'overview'
        ? <AuditPage />
        : <PlaceholderPage section={activeSection} onBack={() => setActiveSection('overview')} />}
    </AppShell>
  )
}

export default App
