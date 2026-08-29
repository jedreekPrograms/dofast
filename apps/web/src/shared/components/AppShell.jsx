import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../features/auth/AuthContext.js'
import { useNotifications } from '../../features/notifications/NotificationContext.js'
import './AppShell.css'

function navClass({ isActive }) {
  return isActive ? 'app-nav__link app-nav__link--active' : 'app-nav__link'
}

function AppShell() {
  const { user, ready, logout } = useAuth()
  const { unreadCount } = useNotifications()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header__inner">
          <Link className="app-brand" to="/">doFast</Link>
          <nav className="app-nav" aria-label="Główna nawigacja">
            <NavLink className={navClass} to="/" end>Zlecenia</NavLink>
            {user && <NavLink className={navClass} to="/my-jobs">Moje zlecenia</NavLink>}
            {user && <NavLink className={navClass} to="/saved-jobs">Zapisane</NavLink>}
            {user && <NavLink className={navClass} to="/saved-searches">Wyszukiwania</NavLink>}
            {user && <NavLink className={navClass} to="/jobs/new">Dodaj zlecenie</NavLink>}
            {user && <NavLink className={navClass} to="/chat">Czaty</NavLink>}
            {user && <NavLink className={navClass} to="/blocked-users">Blokady</NavLink>}
            {user && <NavLink className={navClass} to="/disputes">Spory</NavLink>}
            {user && <NavLink className={navClass} to="/my-reports">Zgłoszenia</NavLink>}
            {user && (
              <NavLink className={navClass} to="/notifications">
                Powiadomienia
                {unreadCount > 0 && <span className="app-nav__badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
              </NavLink>
            )}
            {user && <NavLink className={navClass} to="/wallet">Portfel</NavLink>}
            {user?.role === 'ADMIN' && <NavLink className={navClass} to="/admin">Admin</NavLink>}
            {user?.role === 'ADMIN' && <NavLink className={navClass} to="/admin/disputes">Spory admin</NavLink>}
            {user?.role === 'ADMIN' && <NavLink className={navClass} to="/admin/payouts">Wypłaty admin</NavLink>}
          </nav>
          <div className="app-account">
            {!ready && <span className="app-account__muted">Sesja…</span>}
            {ready && !user && (
              <>
                <Link className="app-account__link" to="/login">Zaloguj się</Link>
                <Link className="button button--primary app-account__button" to="/register">Załóż konto</Link>
              </>
            )}
            {user && (
              <>
                <Link className="app-account__identity" to="/profile">
                  <span>{user.nickname}</span>
                  {user.role === 'ADMIN' && <small>ADMIN</small>}
                </Link>
                <button className="app-account__logout" type="button" onClick={handleLogout}>Wyloguj</button>
              </>
            )}
          </div>
        </div>
      </header>
      <Outlet />
    </div>
  )
}

export default AppShell
