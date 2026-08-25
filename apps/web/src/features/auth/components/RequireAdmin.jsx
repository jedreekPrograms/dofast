import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../AuthContext.js'

function RequireAdmin() {
  const { user } = useAuth()

  if (user?.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}

export default RequireAdmin
