import api from './client'

// ── Auth ──────────────────────────────────────────────────
export const login = (email, password) =>
  api.post('/auth/login', { email, password }).then(r => r.data)

// ── Dashboard ─────────────────────────────────────────────
export const getDashboardSummary = () =>
  api.get('/dashboard/summary').then(r => r.data)

export const getAtRiskCustomers = (limit = 50) =>
  api.get('/dashboard/at-risk', { params: { limit } }).then(r => r.data)

// ── Customers ─────────────────────────────────────────────
export const getCustomers = (page = 0, size = 20) =>
  api.get('/customers', { params: { page, size } }).then(r => r.data)

export const getCustomer = (id) =>
  api.get(`/customers/${id}`).then(r => r.data)

export const getCustomerEvents = (id, page = 0, size = 20) =>
  api.get(`/customers/${id}/events`, { params: { page, size } }).then(r => r.data)

export const getCustomerPredictions = (id) =>
  api.get(`/customers/${id}/predictions`).then(r => r.data)