import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/recommendations', label: 'Recommendations' },
  { to: '/chat', label: 'Chat' },
]

export function DashboardLayout({ children }: { children: ReactNode }) {
  const { username, logout } = useAuth()

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <Link to="/dashboard" className="app-brand">
          cloud-finsight
        </Link>
        <nav className="app-sidebar-nav">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/dashboard'}
              className={({ isActive }) =>
                isActive ? 'app-sidebar-link app-sidebar-link-active' : 'app-sidebar-link'
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="app-content">
        <header className="app-header">
          <div className="app-header-actions">
            <span>{username}</span>
            <button type="button" onClick={logout}>
              Log out
            </button>
          </div>
        </header>
        <main className="app-main">{children}</main>
      </div>
    </div>
  )
}
