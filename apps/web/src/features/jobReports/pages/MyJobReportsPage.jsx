import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyJobReports, withdrawJobReport } from '../api/jobReportsApi.js'
import './MyJobReportsPage.css'

const REASONS = {
  SPAM: 'Spam',
  FRAUD: 'Podejrzenie oszustwa',
  PROHIBITED_CONTENT: 'Niedozwolona treść',
  HARASSMENT: 'Nękanie lub obraźliwe zachowanie',
  OTHER: 'Inny powód',
}

const STATUSES = {
  SUBMITTED: {
    label: 'Oczekuje na moderację',
    description: 'Zgłoszenie zostało zapisane i czeka na ocenę administratora.',
  },
  REVIEWED: {
    label: 'Potwierdzone przez moderację',
    description: 'Moderator potwierdził zgłoszenie. Ewentualne działania wobec oferty lub konta są podejmowane osobno.',
  },
  DISMISSED: {
    label: 'Zamknięte bez potwierdzenia',
    description: 'Moderator zakończył sprawę bez potwierdzenia zgłoszenia.',
  },
  WITHDRAWN: {
    label: 'Wycofane',
    description: 'Wycofałeś zgłoszenie przed rozpoczęciem moderacji. Rekord pozostaje w Twojej historii.',
  },
}

function MyJobReportsPage() {
  const [reports, setReports] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [withdrawingId, setWithdrawingId] = useState(null)

  useEffect(() => {
    let active = true

    getMyJobReports()
      .then((data) => {
        if (active) setReports(data)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać Twoich zgłoszeń.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [])

  async function handleWithdraw(reportId) {
    setError('')
    setWithdrawingId(reportId)
    try {
      const updated = await withdrawJobReport(reportId)
      setReports((current) => current.map((report) => (
        report.id === reportId ? updated : report
      )))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wycofać zgłoszenia.')
    } finally {
      setWithdrawingId(null)
    }
  }

  return (
    <main className="my-reports-page">
      <header className="page-heading">
        <span className="eyebrow">Bezpieczeństwo społeczności</span>
        <h1>Moje zgłoszenia</h1>
        <p>Sprawdź historię ofert zgłoszonych do moderacji i aktualny status każdej sprawy.</p>
      </header>

      <div className="my-reports-page__notice" role="note">
        <strong>Zgłoszenie nie jest sporem płatniczym.</strong>
        <span>Jeśli problem dotyczy aktywnego zlecenia, wykonania lub środków w escrow, skorzystaj z osobnego systemu sporów.</span>
        <Link to="/disputes">Przejdź do sporów</Link>
      </div>

      {error && <div className="form-message form-message--error">{error}</div>}
      {loading && <div className="page-state">Pobieranie zgłoszeń…</div>}

      {!loading && !error && reports.length === 0 && (
        <section className="panel my-reports-empty">
          <h2>Nie masz jeszcze żadnych zgłoszeń</h2>
          <p>Ofertę możesz zgłosić z jej karty w discovery lub na liście zapisanych zleceń.</p>
          <Link className="button button--primary" to="/">Przeglądaj zlecenia</Link>
        </section>
      )}

      {!loading && reports.length > 0 && (
        <section className="my-reports-list" aria-label="Historia zgłoszeń">
          {reports.map((report) => {
            const status = STATUSES[report.status] || {
              label: report.status,
              description: 'Status zgłoszenia został zaktualizowany.',
            }

            return (
              <article className="panel my-report-card" key={report.id}>
                <div className="my-report-card__header">
                  <div>
                    <span className="eyebrow">Zgłoszenie #{report.id}</span>
                    <h2><Link to={`/jobs/${report.jobId}`}>Zlecenie #{report.jobId}</Link></h2>
                  </div>
                  <span className={`my-report-status my-report-status--${report.status.toLowerCase()}`}>
                    {status.label}
                  </span>
                </div>

                <dl className="my-report-card__meta">
                  <div>
                    <dt>Powód</dt>
                    <dd>{REASONS[report.reason] || report.reason}</dd>
                  </div>
                  <div>
                    <dt>Wysłano</dt>
                    <dd>{new Date(report.createdAt).toLocaleString('pl-PL')}</dd>
                  </div>
                  {report.reviewedAt && (
                    <div>
                      <dt>Rozstrzygnięto</dt>
                      <dd>{new Date(report.reviewedAt).toLocaleString('pl-PL')}</dd>
                    </div>
                  )}
                  {report.withdrawnAt && (
                    <div>
                      <dt>Wycofano</dt>
                      <dd>{new Date(report.withdrawnAt).toLocaleString('pl-PL')}</dd>
                    </div>
                  )}
                </dl>

                {report.details && (
                  <div className="my-report-card__details">
                    <strong>Twój opis</strong>
                    <p>{report.details}</p>
                  </div>
                )}

                <p className="my-report-card__status-description">{status.description}</p>

                {report.status === 'SUBMITTED' && (
                  <div className="my-report-card__actions">
                    <button
                      className="button button--secondary"
                      type="button"
                      disabled={withdrawingId === report.id}
                      onClick={() => handleWithdraw(report.id)}
                    >
                      {withdrawingId === report.id ? 'Wycofywanie…' : 'Wycofaj zgłoszenie'}
                    </button>
                    <span>Możesz wycofać zgłoszenie tylko zanim moderator je rozpatrzy.</span>
                  </div>
                )}
              </article>
            )
          })}
        </section>
      )}
    </main>
  )
}

export default MyJobReportsPage
