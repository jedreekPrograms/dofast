import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAdminOverview, getAdminUsers, getFinanceReconciliation, updateAdminUserStatus } from '../api/adminApi.js'
import './AdminPage.css'

const moneyFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

function AdminPage() {
  const [overview, setOverview] = useState(null)
  const [finance, setFinance] = useState(null)
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    let active = true
    Promise.all([getAdminOverview(), getAdminUsers(), getFinanceReconciliation()])
      .then(([overviewData, usersData, financeData]) => {
        if (!active) return
        setOverview(overviewData)
        setUsers(usersData)
        setFinance(financeData)
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
          <p>Zarządzaj kontami, kontroluj spory i monitoruj spójność rozliczeń.</p>
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

      {!loading && finance && (
        <section className={`panel finance-health ${finance.healthy ? 'finance-health--ok' : 'finance-health--alert'}`}>
          <div className="finance-health__heading">
            <div>
              <span className="eyebrow">Reconciliation</span>
              <h2>{finance.healthy ? 'Rozliczenia są spójne' : 'Wykryto niespójność rozliczeń'}</h2>
            </div>
            <strong className="finance-health__badge">{finance.healthy ? 'OK' : 'WYMAGA UWAGI'}</strong>
          </div>
          <div className="finance-health__grid">
            <div><span>Saldo vs ledger</span><strong>{finance.walletBalanceMismatches}</strong></div>
            <div><span>Błędy sekwencji ledgera</span><strong>{finance.ledgerSequenceMismatches}</strong></div>
            <div><span>Stripe vs ledger</span><strong>{finance.stripeLedgerMismatches}</strong></div>
            <div><span>Aktywne escrow</span><strong>{finance.heldEscrowCount}</strong></div>
            <div><span>Środki w escrow</span><strong>{moneyFormatter.format(Number(finance.heldEscrowAmount))}</strong></div>
            <div><span>Rozliczone wpłaty Stripe</span><strong>{finance.processedStripePayments}</strong></div>
          </div>
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
