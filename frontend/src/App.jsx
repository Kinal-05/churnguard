import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { isLoggedIn } from './utils'
import Sidebar from './components/Sidebar'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import CustomersPage from './pages/CustomersPage'
import CustomerDetailPage from './pages/CustomerDetailPage'
import AtRiskPage from './pages/AtRiskPage'

function ProtectedLayout({ children }) {
  if (!isLoggedIn()) return <Navigate to="/login" replace />
  return (
    <div className="app-layout">
      <Sidebar />
      <main className="main-content">{children}</main>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route path="/" element={
          <ProtectedLayout><DashboardPage /></ProtectedLayout>
        } />
        <Route path="/customers" element={
          <ProtectedLayout><CustomersPage /></ProtectedLayout>
        } />
        <Route path="/customers/:id" element={
          <ProtectedLayout><CustomerDetailPage /></ProtectedLayout>
        } />
        <Route path="/at-risk" element={
          <ProtectedLayout><AtRiskPage /></ProtectedLayout>
        } />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}