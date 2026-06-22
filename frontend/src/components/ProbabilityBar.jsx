import { formatPct, riskColor } from '../utils'

export default function ProbabilityBar({ probability, tier }) {
  const pct = probability != null ? probability * 100 : 0
  const color = riskColor(tier)

  return (
    <div className="prob-bar">
      <div className="prob-bar-track">
        <div
          className="prob-bar-fill"
          style={{ width: `${pct}%`, background: color }}
        />
      </div>
      <span className="prob-bar-label" style={{ color }}>
        {formatPct(probability)}
      </span>
    </div>
  )
}