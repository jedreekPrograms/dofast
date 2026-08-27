import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  getAdminJobReports,
  getAdminOverview,
  getAdminUsers,
  getAdminVerifications,
  getFinanceReconciliation,
  updateAdminUserStatus,
} from '../api/adminApi.js'
import './AdminPage.css'

const moneyFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

function AdminPage() {
  const [overview, setOverview] = useState(null)
  const [finance, setFinance] = useState(null)
  const [pendingVerifications, setPendingVerifications] = useState(0)
  const [pendingReports, setPendingReports] = useState(0)
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    let active = true
    Promise.all([
      getAdminOverview(),
      getAdminUsers(),
      getFinanceReconciliation(),
      getAdminVerifications({ status: 'PENDING', page: 0, size: 1 }),
      getAdminJobReports({ status: 'SUBMITTED', page: 0, size: 1 }),
    ])
      .then(([overviewData, usersData, financeData, verificationData, reportData]) => {
        if (!active) return
        setOverview(overviewData)
        setUsers(usersData)
        setFinance(financeData)
        setPendingVerifications(verificationData.totalElements)
        setPendingReports(reportData.totalElements)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać panelu administratora.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  async function reactivateUser(user) {
    setBusyId(user.id)
    setError('')
    try {
      const updated = await updateAdminUserStatus(user.id, 'ACTIVE')
      setUsers((current) => current.map((item) => item.id === updated.id ? updated : item))
      setOverview((current) => current ? {
        ...current,
        activeUsers: current.activeUsers + 1,
        suspendedUsers: current.suspendedUsers - 1,
      } : current)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się ponownie aktywować konta.')
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
          <p>Zarządzaj kontami, kontroluj spory, zgłoszenia, weryfikacje i spójność rozliczeń.</p>
        </div>
        <div className="admin-heading-actions">
          <Link className="button button--secondary" to="/admin/job-reports">Zgłoszenia</Link>
          <Link className="button button--secondary" to="/admin/verifications">Weryfikacje</Link>
          <Link className="button button--primary" to="/admin/disputes">Spory</Link>
        </div>
      </header>

      {loading && <div className="page-state">Pobieranie danych administracyjnych…</div>}
      {error && <div className="form-message form-message--error">{error}</div>}

      {!loading && overview && (
        <section className="admin-stats">
          <div className="panel"><span>Użytkownicy</span><strong>{overview.totalUsers}</strong></div>
          <div className="panel"><span>Aktywne konta</span><strong>{overview.activeUsers}</strong></div>
          <div className="panel"><span>Zawieszone</span><strong>{overview.suspendedUsers}</strong></div>
          <Link className="panel admin-stat-link" to="/admin/verifications">
            <span>Weryfikacje oczekujące</span><strong>{pendingVerifications}</strong>
          </Link>
          <Link className="panel admin-stat-link" to="/admin/job-reports">
            <span>Zgłoszenia oczekujące</span><strong>{pendingReports}</strong>
          </Link>
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
            <div>
              <h2>Konta użytkowników</h2>
              <p>Zawieszenia wykonuj z kolejki potwierdzonych zgłoszeń, aby zachować audyt i zabezpieczenia aktywnych zleceń.</p>
            </div>
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
                {user.status === 'SUSPENDED' && user.role !== 'ADMIN' ? (
                  <button className="button button--secondary" type="button" disabled={busyId === user.id} onClick={() => reactivateUser(user)}>
                    {busyId === user.id ? 'Aktywowanie…' : 'Aktywuj'}
                  </button>
                ) : (
                  <span className="admin-user__role">{user.role === 'ADMIN' ? 'Chronione konto' : 'Sankcja przez zgłoszenie'}</span>
                )}
              </div>
            ))}
          </div>
        </section>
      )}
    </main>
  )
}

export default AdminPage
