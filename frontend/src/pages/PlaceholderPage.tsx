import type { Section } from '../components/layout/AppShell'
import { Icon, type IconName } from '../shared/ui/Icon'

const pageContent: Record<Exclude<Section, 'overview'>, { icon: IconName; title: string; description: string }> = {
  projects: { icon: 'file', title: 'Projects are coming next', description: 'Group multiple domains, compare scores, and share progress with your team.' },
  history: { icon: 'history', title: 'Your audit history', description: 'Completed scans will appear here once the persistence layer is connected.' },
  api: { icon: 'key', title: 'Automate with the AeoLens API', description: 'API keys and CI checks will be available in the next development phase.' },
  settings: { icon: 'settings', title: 'Workspace settings', description: 'Manage team preferences, scoring thresholds, and integrations here.' },
}

interface PlaceholderPageProps {
  section: Exclude<Section, 'overview'>
  onBack: () => void
}

export function PlaceholderPage({ section, onBack }: PlaceholderPageProps) {
  const content = pageContent[section]
  return (
    <div className="page-content">
      <section className="empty-section">
        <span><Icon name={content.icon} size={26} /></span>
        <h1>{content.title}</h1>
        <p>{content.description}</p>
        <button type="button" className="primary-button" onClick={onBack}>Back to overview</button>
      </section>
    </div>
  )
}
