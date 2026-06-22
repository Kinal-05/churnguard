import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getCustomers } from '../api'
import { formatMRR, formatDate } from '../utils'
import RiskBadge from '../components/RiskBadge'
import ProbabilityBar from '../components/ProbabilityBar'

export default function CustomersPage() {
  const [data, setData]       = useState(null)
  const [page, setPage]       = useState(0)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    setLoading(true)
    getCustomers(page, 20)
      .then(setData)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [page])

  return (
    <div>
      <div className="page-header">
        <h2>Customers</h2>
        <p>All customers tracked by ChurnGuard — click any row for full profile</p>
      </div>

      {loading ? (
        <div className="loading"><div className="spinner" /> Loading customers...</div>
      ) : (
        <>
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Plan</th>
                  <th>MRR</th>
                  <th>Status</th>
                  <th>Signup</th>
                  <th>Churn Risk</th>
                  <th>Risk Tier</th>
                </tr>
              </thead>
              <tbody>
                {data?.content?.map(c => (
                  <tr key={c.id} onClick={() => navigate(`/customers/${c.id}`)}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{c.name}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{c.email}</div>
                    </td>
                    <td>
                      <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        {c.plan || '—'}
                      </span>
                    </td>
                    <td>{formatMRR(c.mrrCents)}</td>
                    <td>
                      <span style={{
                        fontSize: 11,
                        fontWeight: 600,
                        color: c.status === 'ACTIVE' ? 'var(--success)'
                             : c.status === 'CHURNED' ? 'var(--danger)'
                             : 'var(--warning)',
                      }}>
                        {c.status}
                      </span>
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {formatDate(c.signupDate)}
                    </td>
                    <td style={{ minWidth: 150 }}>
                      {c.latestPrediction ? (
                        <ProbabilityBar
                          probability={c.latestPrediction.churnProbability}
                          tier={c.latestPrediction.riskTier}
                        />
                      ) : (
                        <span style={{ color: 'var(--text-dim)', fontSize: 12 }}>Not scored</span>
                      )}
                    </td>
                    <td>
                      <RiskBadge tier={c.latestPrediction?.riskTier} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginTop: 20 }}>
              <button
                className="btn btn-outline"
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
              >
                ← Previous
              </button>
              <span style={{ padding: '8px 16px', color: 'var(--text-muted)', fontSize: 13 }}>
                Page {page + 1} of {data.totalPages}
              </span>
              <button
                className="btn btn-outline"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage(p => p + 1)}
              >
                Next →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}