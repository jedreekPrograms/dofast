import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../AuthContext.js'

function RequireAuth() {
  const { user, ready } = useAuth()
  const location = useLocation()

  if (!ready) {
    return <main><div className="page-state">Sprawdzanie sesji…</div></main>
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

export default RequireAuth
