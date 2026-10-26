import { Icon, type IconName } from '../../../shared/ui/Icon'

interface MetricCardProps {
  icon: IconName
  label: string
  value: string
  detail: string
  tone: 'violet' | 'blue' | 'green' | 'red'
}

export function MetricCard({ icon, label, value, detail, tone }: MetricCardProps) {
  return (
    <article className="metric-card">
      <div className={`metric-icon ${tone}`}><Icon name={icon} size={19} /></div>
      <div className="metric-copy"><span>{label}</span><strong>{value}</strong></div>
      <span className={`metric-detail ${tone}`}>{detail}</span>
    </article>
  )
}
