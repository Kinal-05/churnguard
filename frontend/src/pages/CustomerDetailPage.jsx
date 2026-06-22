import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, ReferenceLine,
} from 'recharts'
import { getCustomer, getCustomerEvents, getCustomerPredictions } from '../api'
import { formatMRR, formatDate, formatDateTime, formatPct } from '../utils'
import RiskBadge from '../components/RiskBadge'
import ShapExplanation from '../components/ShapExplanation'

export default function CustomerDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [customer,    setCustomer]    = useState(null)
  const [events,      setEvents]      = useState([])
  const [predictions, setPredictions] = useState([])
  const [loading,     setLoading]     = useState(true)

  useEffect(() => {
    Promise.all([
      getCustomer(id),
      getCustomerEvents(id),
      getCustomerPredictions(id),
    ])
      .then(([c, e, p]) => {
        setCustomer(c)
        setEvents(e)
        setPredictions(p)
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return (
    <div className="loading"><div className="spinner" /> Loading customer...</div>
  )

  if (!customer) return (
    <div className="empty-state">
      <h3>Customer not found</h3>
      <button className="btn btn-outline" onClick={() => navigate('/customers')}>
        ← Back
      </button>
    </div>
  )

  const latest = customer.latestPrediction

  // Build trend data for the risk chart (oldest → newest)
  const trendData = [...predictions]
    .reverse()
    .map(p => ({
      date: formatDate(p.scoredAt),
      probability: parseFloat((p.churnProbability * 100).toFixed(1)),
      tier: p.riskTier,
    }))

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 28 }}>
        <button className="btn btn-outline" onClick={() => navigate(-1)}>← Back</button>
        <div>
          <h2 style={{ fontSize: 22, fontWeight: 700 }}>{customer.name}</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>
            {customer.email} · {customer.plan} · Ref: {customer.externalRef}
          </p>
        </div>
        {latest && (
          <div style={{ marginLeft: 'auto' }}>
            <RiskBadge tier={latest.riskTier} />
          </div>
        )}
      </div>

      {/* Info + latest prediction */}
      <div className="detail-grid">
        <div className="card">
          <div className="card-title">Account Info</div>
          <div className="detail-row">
            <span className="key">Status</span>
            <span className="val" style={{
              color: customer.status === 'ACTIVE' ? 'var(--success)'
                   : customer.status === 'CHURNED' ? 'var(--danger)'
                   : 'var(--warning)'
            }}>
              {customer.status}
            </span>
          </div>
          <div className="detail-row">
            <span className="key">MRR</span>
            <span className="val">{formatMRR(customer.mrrCents)}</span>
          </div>
          <div className="detail-row">
            <span className="key">Plan</span>
            <span className="val">{customer.plan || '—'}</span>
          </div>
          <div className="detail-row">
            <span className="key">Signed up</span>
            <span className="val">{formatDate(customer.signupDate)}</span>
          </div>
          <div className="detail-row">
            <span className="key">Churn probability</span>
            <span className="val" style={{ color: 'var(--danger)' }}>
              {latest ? formatPct(latest.churnProbability) : '—'}
            </span>
          </div>
          <div className="detail-row">
            <span className="key">Revenue at risk</span>
            <span className="val">{latest ? formatMRR(latest.revenueAtRiskCents) : '—'}</span>
          </div>
          <div className="detail-row">
            <span className="key">Model version</span>
            <span className="val">{latest?.modelVersion || '—'}</span>
          </div>
          <div className="detail-row">
            <span className="key">Last scored</span>
            <span className="val">{latest ? formatDateTime(latest.scoredAt) : '—'}</span>
          </div>
        </div>

        {/* SHAP explanation */}
        <div className="card">
          <div className="card-title">Why is this customer at risk?</div>
          {latest?.explanation ? (
            <ShapExplanation explanation={latest.explanation} />
          ) : (
            <div className="empty-state" style={{ padding: '20px 0' }}>
              <p>No prediction yet — events need to be ingested first</p>
            </div>
          )}
        </div>
      </div>

      {/* Risk trend chart */}
      {trendData.length > 1 && (
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="card-title">Churn Risk Trend</div>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={trendData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2a2d3e" />
              <XAxis dataKey="date" tick={{ fill: '#8892a4', fontSize: 11 }} axisLine={false} />
              <YAxis
                domain={[0, 100]}
                tick={{ fill: '#8892a4', fontSize: 11 }}
                axisLine={false}
                tickFormatter={v => `${v}%`}
              />
              <Tooltip
                contentStyle={{ background: '#1a1d27', border: '1px solid #2a2d3e', borderRadius: 8 }}
                formatter={v => [`${v}%`, 'Churn probability']}
              />
              <ReferenceLine y={50} stroke="#ef4444" strokeDasharray="4 4" opacity={0.5} />
              <ReferenceLine y={75} stroke="#dc2626" strokeDasharray="4 4" opacity={0.5} />
              <Line
                type="monotone"
                dataKey="probability"
                stroke="#6366f1"
                strokeWidth={2}
                dot={{ fill: '#6366f1', r: 4 }}
                activeDot={{ r: 6 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Recent events */}
      <div className="card">
        <div className="card-title" style={{ marginBottom: 16 }}>Recent Events</div>
        {events.length === 0 ? (
          <div className="empty-state" style={{ padding: '20px 0' }}>
            <p>No events recorded yet</p>
          </div>
        ) : (
          <div className="table-wrapper" style={{ border: 'none' }}>
            <table>
              <thead>
                <tr>
                  <th>Event Type</th>
                  <th>Occurred</th>
                  <th>Payload</th>
                </tr>
              </thead>
              <tbody>
                {events.slice(0, 20).map(e => (
                  <tr key={e.id}>
                    <td>
                      <span style={{
                        fontSize: 11, fontWeight: 700, padding: '2px 8px',
                        borderRadius: 4, background: 'var(--bg)',
                        color: e.eventType === 'PAYMENT_FAILED' ? 'var(--danger)'
                             : e.eventType === 'SUPPORT_TICKET' ? 'var(--warning)'
                             : e.eventType === 'LOGIN' ? 'var(--success)'
                             : 'var(--text-muted)',
                      }}>
                        {e.eventType}
                      </span>
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {formatDateTime(e.occurredAt)}
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {e.eventPayload && Object.keys(e.eventPayload).length > 0
                        ? JSON.stringify(e.eventPayload)
                        : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}