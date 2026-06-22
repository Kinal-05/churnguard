// Simple auth helpers — no context library needed for this scope

export const saveAuth = (data) => {
  localStorage.setItem('token', data.token)
  localStorage.setItem('user', JSON.stringify({
    email: data.email,
    role: data.role,
    tenantId: data.tenantId,
  }))
}

export const getUser = () => {
  try {
    return JSON.parse(localStorage.getItem('user'))
  } catch {
    return null
  }
}

export const isLoggedIn = () => !!localStorage.getItem('token')

export const logout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  window.location.href = '/login'
}

// ── Formatting helpers ────────────────────────────────────
export const formatMRR = (cents) => {
  if (!cents && cents !== 0) return '—'
  return `$${(cents / 100).toLocaleString('en-US', { minimumFractionDigits: 0 })}`
}

export const formatPct = (prob) => {
  if (prob === null || prob === undefined) return '—'
  return `${(prob * 100).toFixed(1)}%`
}

export const formatDate = (iso) => {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric'
  })
}

export const formatDateTime = (iso) => {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

export const riskColor = (tier) => ({
  LOW: 'var(--risk-low)',
  MEDIUM: 'var(--risk-medium)',
  HIGH: 'var(--risk-high)',
  CRITICAL: 'var(--risk-critical)',
}[tier] || 'var(--text-muted)')