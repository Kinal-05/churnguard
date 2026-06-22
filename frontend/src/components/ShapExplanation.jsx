export default function ShapExplanation({ explanation }) {
  if (!explanation || explanation.length === 0) {
    return <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>No explanation available.</p>
  }

  const maxImpact = Math.max(...explanation.map(f => Math.abs(f.impact)))

  return (
    <ul className="explanation-list">
      {explanation.map((factor, i) => {
        const pct = maxImpact > 0 ? (Math.abs(factor.impact) / maxImpact) * 100 : 0
        const isRisk = factor.direction === 'increases_risk'

        return (
          <li key={i} className="explanation-item">
            <div className="feat-name">
              <span>{factor.description || factor.feature}</span>
              <span style={{ color: isRisk ? 'var(--danger)' : 'var(--success)', fontSize: 11 }}>
                {isRisk ? '↑ risk' : '↓ risk'} {Math.abs(factor.impact).toFixed(3)}
              </span>
            </div>
            <div className="shap-bar-track">
              <div
                className={`shap-bar-fill ${isRisk ? 'increases' : 'decreases'}`}
                style={{ width: `${pct}%` }}
              />
            </div>
          </li>
        )
      })}
    </ul>
  )
}