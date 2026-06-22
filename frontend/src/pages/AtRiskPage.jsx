import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getAtRiskCustomers } from '../api'
import { formatMRR, formatDateTime } from '../utils'
import RiskBadge from '../components/RiskBadge'
import ProbabilityBar from '../components/ProbabilityBar'
import ShapExplanation from '../components/ShapExplanation'

export default function AtRiskPage() {
  const [customers, setCustomers] = useState([])
  const [loading, setLoading]     = useState(true)
  const [expanded, setExpanded]   = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    getAtRiskCustomers(50)
      .then(setCustomers)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  if (loading) return (
    <div className="loading"><div className="spinner" /> Loading at-risk customers...</div>
  )

  return (
    <div>
      <div className="page-header">
        <h2>🚨 At-Risk Accounts</h2>
        <p>
          {customers.length} customers with Medium, High, or Critical churn risk —
          sorted by probability descending
        </p>
      </div>

      {customers.length === 0 ? (
        <div className="empty-state card">
          <h3>No at-risk customers</h3>
          <p>
            Either all customers are low risk, or the ML service hasn't scored anyone yet.
            Ingest some events via the API to trigger scoring.
          </p>
        </div>
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Customer</th>
                <th>Plan</th>
                <th>MRR</th>
                <th>Churn Risk</th>
                <th>Tier</th>
                <th>Rev at Risk</th>
                <th>Top Risk Factor</th>
                <th>Scored</th>
              </tr>
            </thead>
            <tbody>
              {customers.map(c => (
                <>
                  <tr
                    key={c.customerId}
                    onClick={() => setExpanded(expanded === c.customerId ? null : c.customerId)}
                    style={{ borderBottom: expanded === c.customerId ? 'none' : undefined }}
                  >
                    <td>
                      <div style={{ fontWeight: 600 }}>{c.customerName}</div>
                    </td>
                    <td>
                      <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        {c.plan || '—'}
                      </span>
                    </td>
                    <td>{formatMRR(c.mrrCents)}</td>
                    <td style={{ minWidth: 160 }}>
                      <ProbabilityBar probability={c.churnProbability} tier={c.riskTier} />
                    </td>
                    <td><RiskBadge tier={c.riskTier} /></td>
                    <td style={{ color: 'var(--danger)', fontWeight: 600 }}>
                      {formatMRR(c.revenueAtRiskCents)}
                    </td>
                    <td style={{ fontSize: 12, color: 'var(--text-muted)', maxWidth: 200 }}>
                      {c.topFactors?.[0]?.description || '—'}
                    </td>
                    <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                      {formatDateTime(c.scoredAt)}
                    </td>
                  </tr>

                  {/* Expandable SHAP explanation row */}
                  {expanded === c.customerId && (
                    <tr key={`${c.customerId}-detail`}>
                      <td colSpan={8} style={{ padding: '16px 20px', background: 'var(--bg)' }}>
                        <div style={{
                          display: 'grid',
                          gridTemplateColumns: '1fr auto',
                          gap: 20,
                          alignItems: 'start',
                        }}>
                          <div>
                            <div
                              className="card-title"
                              style={{ marginBottom: 12 }}
                            >
                              Why is {c.customerName} at risk?
                            </div>
                            <ShapExplanation explanation={c.topFactors} />
                          </div>
                          <button
                            className="btn btn-primary"
                            onClick={(e) => {
                              e.stopPropagation()
                              navigate(`/customers/${c.customerId}`)
                            }}
                          >
                            Full Profile →
                          </button>
                        </div>
                      </td>
                    </tr>
                  )}
                </>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}