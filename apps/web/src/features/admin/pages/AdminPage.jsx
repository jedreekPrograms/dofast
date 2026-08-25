import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAdminOverview, getAdminUsers, updateAdminUserStatus } from '../api/adminApi.js'
import './AdminPage.css'

function AdminPage() {
  const [overview, setOverview] = useState(null)
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    let active = true
    Promise.all([getAdminOverview(), getAdminUsers()])
      .then(([overviewData, usersData]) => {
        if (!active) return
        setOverview(overviewData)
        setUsers(usersData)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać panelu administratora.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  async function toggleStatus(user) {
    const nextStatus = user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
    setBusyId(user.id)
    setError('')
    try {
      const updated = await updateAdminUserStatus(user.id, nextStatus)
      setUsers((current) => current.map((item) => item.id === updated.id ? updated : item))
      setOverview((current) => current ? {
        ...current,
        activeUsers: current.activeUsers + (nextStatus === 'ACTIVE' ? 1 : -1),
        suspendedUsers: current.suspendedUsers + (nextStatus === 'SUSPENDED' ? 1 : -1),
      } : current)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zmienić statusu konta.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <main className="admin-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Administracja</span>
          <h1>Panel administratora</h1>
          <p>Zarządzaj kontami użytkowników oraz przechodź do kolejki sporów powiązanej bezpośrednio z escrow.</p>
        </div>
        <Link className="button button--primary" to="/admin/disputes">Przejdź do sporów</Link>
      </header>

      {loading && <div className="page-state">Pobieranie danych administracyjnych…</div>}
      {error && <div className="form-message form-message--error">{error}</div>}

      {!loading && overview && (
        <section className="admin-stats">
          <div className="panel"><span>Użytkownicy</span><strong>{overview.totalUsers}</strong></div>
          <div className="panel"><span>Aktywne konta</span><strong>{overview.activeUsers}</strong></div>
          <div className="panel"><span>Zawieszone</span><strong>{overview.suspendedUsers}</strong></div>
        </section>
      )}

      {!loading && (
        <section className="panel admin-users">
          <div className="admin-users__heading">
            <div><h2>Konta użytkowników</h2><p>Rola ADMIN nie może być nadawana przez publiczną rejestrację.</p></div>
          </div>
          <div className="admin-users__list">
            {users.map((user) => (
              <div className="admin-user" key={user.id}>
                <div>
                  <strong>{user.nickname}</strong>
                  <span>{user.email}</span>
                </div>
                <span className="admin-user__role">{user.role}</span>
                <span className={`status-pill ${user.status === 'SUSPENDED' ? 'status-pill--cancelled' : 'status-pill--done'}`}>{user.status}</span>
                <button className="button button--secondary" type="button" disabled={user.role === 'ADMIN' || busyId === user.id} onClick={() => toggleStatus(user)}>
                  {user.status === 'ACTIVE' ? 'Zawieś' : 'Aktywuj'}
                </button>
              </div>
            ))}
          </div>
        </section>
      )}
    </main>
  )
}

export default AdminPage
