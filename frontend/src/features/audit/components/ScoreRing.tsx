import type { CSSProperties } from 'react'

interface ScoreRingProps {
  score: number
  label: string
  color: string
  compact?: boolean
}

export function ScoreRing({ score, label, color, compact = false }: ScoreRingProps) {
  const ringStyle = { '--score': `${score * 3.6}deg`, '--ring-color': color } as CSSProperties
  return (
    <div className={`score-ring-wrap ${compact ? 'compact' : ''}`}>
      <div className="score-ring" style={ringStyle}>
        <div className="score-ring-inner"><strong>{score}</strong>{!compact && <span>/ 100</span>}</div>
      </div>
      <div className="score-label"><span>{label}</span><small>{score >= 85 ? 'Excellent' : score >= 70 ? 'Good' : 'Needs work'}</small></div>
    </div>
  )
}
