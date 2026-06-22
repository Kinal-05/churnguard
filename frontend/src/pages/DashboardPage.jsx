import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Legend,
} from 'recharts'
import { getDashboardSummary, getAtRiskCustomers } from '../api'
import { formatMRR, formatPct, formatDateTime } from '../utils'
import RiskBadge from '../components/RiskBadge'
import ProbabilityBar from '../components/ProbabilityBar'

const RISK_COLORS = {
  CRITICAL: '#dc2626',
  HIGH:     '#ef4444',
  MEDIUM:   '#f59e0b',
  LOW:      '#10b981',
}

export default function DashboardPage() {
  const [summary, setSummary]   = useState(null)
  const [atRisk, setAtRisk]     = useState([])
  const [loading, setLoading]   = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    Promise.all([getDashboardSummary(), getAtRiskCustomers(10)])
      .then(([s, r]) => { setSummary(s); setAtRisk(r) })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  if (loading) return (
    <div className="loading"><div className="spinner" /> Loading dashboard...</div>
  )

  const pieData = summary ? [
    { name: 'Critical', value: summary.criticalRiskCount,  color: RISK_COLORS.CRITICAL },
    { name: 'High',     value: summary.highRiskCount,      color: RISK_COLORS.HIGH },
    { name: 'Medium',   value: summary.mediumRiskCount,    color: RISK_COLORS.MEDIUM },
    { name: 'Low',      value: summary.lowRiskCount,       color: RISK_COLORS.LOW },
  ].filter(d => d.value > 0) : []

  const barData = summary ? [
    { tier: 'Critical', count: summary.criticalRiskCount,  fill: RISK_COLORS.CRITICAL },
    { tier: 'High',     count: summary.highRiskCount,      fill: RISK_COLORS.HIGH },
    { tier: 'Medium',   count: summary.mediumRiskCount,    fill: RISK_COLORS.MEDIUM },
    { tier: 'Low',      count: summary.lowRiskCount,       fill: RISK_COLORS.LOW },
  ] : []

  return (
    <div>
      <div className="page-header">
        <h2>Dashboard</h2>
        <p>Real-time churn risk across your customer base</p>
      </div>

      {/* ── Stat cards ── */}
      {summary && (
        <div className="stats-grid">
          <div className="stat-card primary">
            <div className="label">Active Customers</div>
            <div className="value">{summary.totalActiveCustomers.toLocaleString()}</div>
            <div className="sub">Model: {summary.activeModelVersion}</div>
          </div>
          <div className="stat-card danger">
            <div className="label">Critical Risk</div>
            <div className="value">{summary.criticalRiskCount}</div>
            <div className="sub">Needs immediate action</div>
          </div>
          <div className="stat-card warning">
            <div className="label">High Risk</div>
            <div className="value">{summary.highRiskCount}</div>
            <div className="sub">Monitor closely</div>
          </div>
          <div className="stat-card danger">
            <div className="label">Revenue at Risk</div>
            <div className="value">{formatMRR(summary.totalRevenueAtRiskCents)}</div>
            <div className="sub">High + Critical MRR</div>
          </div>
        </div>
      )}

      {/* ── Charts row ── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 28 }}>
        <div className="card">
          <div className="card-title">Risk Distribution</div>
          {pieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={pieData}
                  cx="50%" cy="50%"
                  innerRadius={55} outerRadius={85}
                  dataKey="value"
                  label={({ name, value }) => `${name}: ${value}`}
                  labelLine={false}
                >
                  {pieData.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ background: '#1a1d27', border: '1px solid #2a2d3e', borderRadius: 8 }}
                  itemStyle={{ color: '#e2e8f0' }}
                />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="empty-state">
              <p>No predictions yet — run the ML service to score customers</p>
            </div>
          )}
        </div>

        <div className="card">
          <div className="card-title">Customers by Risk Tier</div>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={barData} barSize={36}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2a2d3e" />
              <XAxis dataKey="tier" tick={{ fill: '#8892a4', fontSize: 12 }} axisLine={false} />
              <YAxis tick={{ fill: '#8892a4', fontSize: 12 }} axisLine={false} />
              <Tooltip
                contentStyle={{ background: '#1a1d27', border: '1px solid #2a2d3e', borderRadius: 8 }}
                itemStyle={{ color: '#e2e8f0' }}
              />
              <Bar dataKey="count" name="Customers" radius={[4, 4, 0, 0]}>
                {barData.map((entry, i) => (
                  <Cell key={i} fill={entry.fill} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* ── Top at-risk table ── */}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div className="card-title" style={{ marginBottom: 0 }}>Top At-Risk Accounts</div>
          <button className="btn btn-outline" onClick={() => navigate('/at-risk')}>
            View all →
          </button>
        </div>

        {atRisk.length === 0 ? (
          <div className="empty-state">
            <h3>No at-risk customers yet</h3>
            <p>Predictions will appear here once the ML service has scored customers</p>
          </div>
        ) : (
          <div className="table-wrapper" style={{ border: 'none' }}>
            <table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Plan</th>
                  <th>MRR</th>
                  <th>Churn Risk</th>
                  <th>Risk Tier</th>
                  <th>Rev at Risk</th>
                  <th>Scored</th>
                </tr>
              </thead>
              <tbody>
                {atRisk.map(c => (
                  <tr key={c.customerId} onClick={() => navigate(`/customers/${c.customerId}`)}>
                    <td style={{ fontWeight: 600 }}>{c.customerName}</td>
                    <td><span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{c.plan || '—'}</span></td>
                    <td>{formatMRR(c.mrrCents)}</td>
                    <td style={{ minWidth: 160 }}>
                      <ProbabilityBar probability={c.churnProbability} tier={c.riskTier} />
                    </td>
                    <td><RiskBadge tier={c.riskTier} /></td>
                    <td style={{ color: 'var(--danger)', fontWeight: 600 }}>
                      {formatMRR(c.revenueAtRiskCents)}
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {formatDateTime(c.scoredAt)}
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