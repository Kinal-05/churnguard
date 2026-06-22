export default function RiskBadge({ tier }) {
  if (!tier) return <span className="risk-badge LOW">—</span>
  return <span className={`risk-badge ${tier}`}>{tier}</span>
}