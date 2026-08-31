import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  enforceAdminJobReport,
  enforceAdminJobReportAccount,
  getAdminJobReportAccountEnforcement,
  getAdminJobReportEnforcement,
  getAdminJobReports,
  moderateAdminJobReport,
} from '../api/adminApi.js'
import './AdminJobReportsPage.css'

const STATUS_LABELS = {
  SUBMITTED: 'Oczekuje',
  REVIEWED: 'Potwierdzone',
  DISMISSED: 'Odrzucone',
}

const REASON_LABELS = {
  SPAM: 'Spam',
  FRAUD: 'Podejrzenie oszustwa',
  PROHIBITED_CONTENT: 'Niedozwolona treść',
  HARASSMENT: 'Nękanie',
  OTHER: 'Inny powód',
}

const ACCOUNT_ENFORCEMENT_LABELS = {
  SUSPEND_JOB_OWNER: 'Zawieszenie właściciela zlecenia',
  EMERGENCY_SUSPEND_JOB_OWNER: 'Awaryjne zawieszenie z zabezpieczeniem aktywnych transakcji',
}

function AdminJobReportsPage() {
  const [pageData, setPageData] = useState(null)
  const [status, setStatus] = useState('SUBMITTED')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState(null)
  const [decision, setDecision] = useState('REVIEWED')
  const [note, setNote] = useState('')
  const [enforcement, setEnforcement] = useState(null)
  const [enforcementReason, setEnforcementReason] = useState('')
  const [accountEnforcement, setAccountEnforcement] = useState(null)
  const [accountEnforcementReason, setAccountEnforcementReason] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [enforcementLoading, setEnforcementLoading] = useState(false)
  const [error, setError] = useState('')

  const loadQueue = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await getAdminJobReports({ status, page, size: 20 })
      setPageData(data)
      setSelected((current) => {
        if (!current) return null
        return data.content.find((report) => report.id === current.id) ?? null
      })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać zgłoszeń.')
    } finally {
      setLoading(false)
    }
  }, [page, status])

  useEffect(() => {
    loadQueue()
  }, [loadQueue])

  async function loadEnforcements(reportId) {
    setEnforcementLoading(true)
    try {
      const [listingAudit, accountAudit] = await Promise.all([
        getAdminJobReportEnforcement(reportId),
        getAdminJobReportAccountEnforcement(reportId),
      ])
      setEnforcement(listingAudit)
      setAccountEnforcement(accountAudit)
    } catch (requestError) {
      setEnforcement(null)
      setAccountEnforcement(null)
      setError(requestError.message || 'Nie udało się pobrać historii egzekucji.')
    } finally {
      setEnforcementLoading(false)
    }
  }

  function selectReport(report) {
    setSelected(report)
    setDecision('REVIEWED')
    setNote('')
    setEnforcement(null)
    setEnforcementReason('')
    setAccountEnforcement(null)
    setAccountEnforcementReason('')
    setError('')
    loadEnforcements(report.id)
  }

  async function submitDecision(event) {
    event.preventDefault()
    if (!selected || selected.status !== 'SUBMITTED') return

    setBusy(true)
    setError('')
    try {
      const updated = await moderateAdminJobReport(selected.id, decision, note)
      setSelected(updated)
      setNote('')
      await loadEnforcements(updated.id)
      await loadQueue()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zapisać decyzji moderacyjnej.')
    } finally {
      setBusy(false)
    }
  }

  async function submitEnforcement(event) {
    event.preventDefault()
    if (!selected || selected.status !== 'REVIEWED' || enforcement) return

    setBusy(true)
    setError('')
    try {
      const audit = await enforceAdminJobReport(selected.id, enforcementReason)
      setEnforcement(audit)
      setEnforcementReason('')
      await loadQueue()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wykonać akcji egzekucyjnej.')
    } finally {
      setBusy(false)
    }
  }

  async function submitAccountEnforcement(event) {
    event.preventDefault()
    if (!selected || selected.status !== 'REVIEWED' || accountEnforcement) return

    const action = event.nativeEvent.submitter?.value || 'SUSPEND_JOB_OWNER'
    setBusy(true)
    setError('')
    try {
      const audit = await enforceAdminJobReportAccount(selected.id, accountEnforcementReason, action)
      setAccountEnforcement(audit)
      setAccountEnforcementReason('')
      await loadQueue()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wykonać sankcji wobec konta właściciela zlecenia.')
    } finally {
      setBusy(false)
    }
  }

  function changeStatus(nextStatus) {
    setStatus(nextStatus)
    setPage(0)
    setSelected(null)
    setEnforcement(null)
    setAccountEnforcement(null)
  }

  return (
    <main className="admin-reports-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Administracja · trust & safety</span>
          <h1>Zgłoszenia zleceń</h1>
          <p>Przeglądaj prywatne zgłoszenia użytkowników i zapisuj audytowalne decyzje moderacyjne.</p>
        </div>
        <label className="admin-reports-filter">
          Status
          <select value={status} onChange={(event) => changeStatus(event.target.value)}>
            <option value="">Wszystkie</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
      </header>

      {error && <div className="form-message form-message--error">{error}</div>}

      <div className="admin-reports-layout">
        <section className="panel admin-reports-queue">
          <div className="admin-reports-queue__heading">
            <div><span className="eyebrow">Kolejka</span><h2>Zgłoszenia</h2></div>
            {pageData && <strong>{pageData.totalElements}</strong>}
          </div>

          {loading && <div className="page-state">Pobieranie zgłoszeń…</div>}
          {!loading && pageData?.content.length === 0 && <div className="page-state">Brak zgłoszeń dla wybranego filtra.</div>}

          <div className="admin-report-list">
            {pageData?.content.map((report) => (
              <button
                className={`admin-report-row ${selected?.id === report.id ? 'admin-report-row--active' : ''}`}
                type="button"
                key={report.id}
                onClick={() => selectReport(report)}
              >
                <div>
                  <strong>Zgłoszenie #{report.id} · zlecenie #{report.jobId}</strong>
                  <span>{REASON_LABELS[report.reason] || report.reason} · {report.reporterEmail}</span>
                </div>
                <span className={`status-pill status-pill--${report.status.toLowerCase()}`}>
                  {STATUS_LABELS[report.status] || report.status}
                </span>
              </button>
            ))}
          </div>

          {pageData && pageData.totalPages > 1 && (
            <div className="pagination">
              <button type="button" disabled={pageData.first} onClick={() => setPage((value) => Math.max(0, value - 1))}>Poprzednia</button>
              <span>{pageData.page + 1} / {pageData.totalPages}</span>
              <button type="button" disabled={pageData.last} onClick={() => setPage((value) => value + 1)}>Następna</button>
            </div>
          )}
        </section>

        <section className="panel admin-report-detail">
          {!selected && <div className="page-state">Wybierz zgłoszenie z kolejki, aby zobaczyć szczegóły.</div>}

          {selected && (
            <>
              <div className="admin-report-detail__heading">
                <div>
                  <span className="eyebrow">Zgłoszenie #{selected.id}</span>
                  <h2>{REASON_LABELS[selected.reason] || selected.reason}</h2>
                </div>
                <span className={`status-pill status-pill--${selected.status.toLowerCase()}`}>
                  {STATUS_LABELS[selected.status] || selected.status}
                </span>
              </div>

              <div className="admin-report-facts">
                <div><span>Zlecenie</span><strong>#{selected.jobId}</strong></div>
                <div><span>Zgłaszający</span><strong>#{selected.reporterId}</strong></div>
                <div><span>E-mail</span><strong>{selected.reporterEmail}</strong></div>
                <div><span>Utworzono</span><strong>{new Date(selected.createdAt).toLocaleString('pl-PL')}</strong></div>
              </div>

              <div className="admin-report-description">
                <strong>Opis zgłoszenia</strong>
                <p>{selected.details || 'Użytkownik nie dodał dodatkowego opisu.'}</p>
              </div>

              <div className="admin-report-links">
                <Link className="button button--secondary" to={`/jobs/${selected.jobId}`}>Otwórz publiczny widok zlecenia</Link>
                <Link className="button button--secondary" to={`/users/${selected.reporterId}`}>Profil zgłaszającego</Link>
              </div>

              {selected.status === 'SUBMITTED' && (
                <form className="admin-report-decision" onSubmit={submitDecision}>
                  <h3>Decyzja moderacyjna</h3>
                  <label>
                    Wynik analizy
                    <select value={decision} onChange={(event) => setDecision(event.target.value)}>
                      <option value="REVIEWED">Potwierdź zgłoszenie</option>
                      <option value="DISMISSED">Odrzuć zgłoszenie</option>
                    </select>
                  </label>
                  <label>
                    Notatka wewnętrzna
                    <textarea
                      rows={5}
                      maxLength={1000}
                      value={note}
                      onChange={(event) => setNote(event.target.value)}
                      placeholder="Opcjonalne uzasadnienie decyzji dla audytu moderacji."
                    />
                  </label>
                  <button className="button button--primary" type="submit" disabled={busy}>
                    {busy ? 'Zapisywanie…' : 'Zapisz decyzję'}
                  </button>
                </form>
              )}

              {selected.status !== 'SUBMITTED' && (
                <div className="admin-report-audit">
                  <h3>Audit trail moderacji</h3>
                  <div><span>Rozpatrzono</span><strong>{selected.reviewedAt ? new Date(selected.reviewedAt).toLocaleString('pl-PL') : '—'}</strong></div>
                  <div><span>Moderator</span><strong>{selected.reviewedById ? `#${selected.reviewedById}` : '—'}</strong></div>
                  <div><span>Notatka</span><strong>{selected.moderationNote || 'Brak notatki'}</strong></div>
                </div>
              )}

              {enforcementLoading && <div className="page-state">Pobieranie historii egzekucji…</div>}

              {!enforcementLoading && selected.status === 'REVIEWED' && (
                <div className="admin-report-enforcement-grid">
                  {!enforcement ? (
                    <form className="admin-report-enforcement" onSubmit={submitEnforcement}>
                      <div>
                        <span className="eyebrow">Egzekucja wobec zlecenia</span>
                        <h3>Anuluj otwarte zlecenie</h3>
                        <p>
                          Operacja jest audytowana i możliwa tylko dla statusu OPEN. Aktywne zlecenia,
                          escrow i live tracking pozostają chronione.
                        </p>
                      </div>
                      <label>
                        Powód egzekucji
                        <textarea
                          rows={4}
                          maxLength={1000}
                          value={enforcementReason}
                          onChange={(event) => setEnforcementReason(event.target.value)}
                          placeholder="Opcjonalny wewnętrzny powód zapisany w audycie."
                        />
                      </label>
                      <button className="button button--danger" type="submit" disabled={busy}>
                        {busy ? 'Wykonywanie…' : 'Anuluj otwarte zlecenie'}
                      </button>
                    </form>
                  ) : (
                    <div className="admin-report-enforcement-audit">
                      <h3>Audit egzekucji zlecenia</h3>
                      <div><span>Akcja</span><strong>Anulowanie otwartego zlecenia</strong></div>
                      <div><span>Wykonano</span><strong>{new Date(enforcement.createdAt).toLocaleString('pl-PL')}</strong></div>
                      <div><span>Moderator</span><strong>#{enforcement.moderatorId}</strong></div>
                      <div><span>Powód</span><strong>{enforcement.reason || 'Brak dodatkowego powodu'}</strong></div>
                    </div>
                  )}

                  {!accountEnforcement ? (
                    <form className="admin-report-account-enforcement" onSubmit={submitAccountEnforcement}>
                      <div>
                        <span className="eyebrow">Egzekucja wobec konta</span>
                        <h3>Zawieś właściciela zlecenia</h3>
                        <p>
                          Zwykłe zawieszenie anuluje otwarte oferty, ale zostaje odrzucone, jeśli użytkownik ma aktywne,
                          finalizowane lub sporne zlecenie. Tryb awaryjny jest przeznaczony wyłącznie dla pilnego ryzyka:
                          zabezpiecza aktywne prace jako spory moderacyjne, pozostawia escrow zamrożone, wyłącza live tracking,
                          unieważnia sesje i dopiero potem zawiesza konto.
                        </p>
                      </div>
                      <label>
                        Powód zawieszenia
                        <textarea
                          rows={4}
                          maxLength={1000}
                          value={accountEnforcementReason}
                          onChange={(event) => setAccountEnforcementReason(event.target.value)}
                          placeholder="Opcjonalny wewnętrzny powód zapisany w audycie konta."
                        />
                      </label>
                      <button
                        className="button button--danger"
                        type="submit"
                        value="SUSPEND_JOB_OWNER"
                        disabled={busy}
                      >
                        {busy ? 'Wykonywanie…' : 'Zawieś konto bez aktywnych transakcji'}
                      </button>
                      <button
                        className="button button--danger"
                        type="submit"
                        value="EMERGENCY_SUSPEND_JOB_OWNER"
                        disabled={busy}
                      >
                        {busy ? 'Wykonywanie…' : 'Awaryjnie zabezpiecz transakcje i zawieś'}
                      </button>
                    </form>
                  ) : (
                    <div className="admin-report-account-enforcement-audit">
                      <h3>Audit zawieszenia konta</h3>
                      <div>
                        <span>Akcja</span>
                        <strong>{ACCOUNT_ENFORCEMENT_LABELS[accountEnforcement.action] || accountEnforcement.action}</strong>
                      </div>
                      <div><span>Użytkownik</span><strong>#{accountEnforcement.targetUserId}</strong></div>
                      <div><span>Wykonano</span><strong>{new Date(accountEnforcement.createdAt).toLocaleString('pl-PL')}</strong></div>
                      <div><span>Moderator</span><strong>#{accountEnforcement.moderatorId}</strong></div>
                      <div><span>Powód</span><strong>{accountEnforcement.reason || 'Brak dodatkowego powodu'}</strong></div>
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </main>
  )
}

export default AdminJobReportsPage
