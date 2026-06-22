import { NavLink } from 'react-router-dom'
import { logout, getUser } from '../utils'

export default function Sidebar() {
  const user = getUser()

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <h1>⚡ ChurnGuard</h1>
        <span>Revenue Intelligence</span>
      </div>

      <nav>
        <NavLink to="/" end className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          📊 Dashboard
        </NavLink>
        <NavLink to="/customers" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          👥 Customers
        </NavLink>
        <NavLink to="/at-risk" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          🚨 At Risk
        </NavLink>
      </nav>

      <div className="sidebar-footer">
        <div style={{ marginBottom: 8, fontWeight: 600, color: 'var(--text)' }}>
          {user?.email}
        </div>
        <div style={{ fontSize: 11, marginBottom: 12, color: 'var(--text-dim)' }}>
          {user?.role}
        </div>
        <button
          className="btn btn-outline"
          style={{ width: '100%', justifyContent: 'center', fontSize: 12 }}
          onClick={logout}
        >
          Sign out
        </button>
      </div>
    </aside>
  )
}