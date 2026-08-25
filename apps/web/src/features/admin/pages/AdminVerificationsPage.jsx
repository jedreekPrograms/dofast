import { useEffect, useState } from 'react'
import {
  decideAdminVerification,
  getAdminVerificationEvents,
  getAdminVerifications,
} from '../api/adminApi.js'
import './AdminVerificationsPage.css'

const statusLabels = {
  PENDING: 'Oczekuje',
  VERIFIED: 'Zweryfikowana',
  REJECTED: 'Odrzucona',
  REVOKED: 'Cofnięta',
}

const eventLabels = {
  REQUESTED: 'Zgłoszono',
  RESUBMITTED: 'Zgłoszono ponownie',
  APPROVED: 'Zatwierdzono',
  REJECTED: 'Odrzucono',
  REVOKED: 'Cofnięto',
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('pl-PL') : '—'
}

function AdminVerificationsPage() {
  const [status, setStatus] = useState('PENDING')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)
  const [reasonById, setReasonById] = useState({})
  const [eventsById, setEventsById] = useState({})
  const [eventsLoadingId, setEventsLoadingId] = useState(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getAdminVerifications({ status, page, size: 20 })
      .then((response) => {
        if (active) setData(response)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać weryfikacji.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [status, page])

  async function decide(item, decision) {
    const reason = reasonById[item.id] || ''
    setBusyId(item.id)
    setError('')
    try {
      const updated = await decideAdminVerification(item.id, decision, reason)
      setData((current) => {
        if (!current) return current
        if (!status) {
          return {
            ...current,
            content: current.content.map((entry) => entry.id === updated.id ? updated : entry),
          }
        }
        return {
          ...current,
          content: current.content.filter((entry) => entry.id !== updated.id),
          totalElements: Math.max(0, current.totalElements - 1),
        }
      })
      setReasonById((current) => ({ ...current, [item.id]: '' }))
      setEventsById((current) => {
        const next = { ...current }
        delete next[item.id]
        return next
      })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zapisać decyzji.')
    } finally {
      setBusyId(null)
    }
  }

  async function toggleEvents(item) {
    if (eventsById[item.id]) {
      setEventsById((current) => {
        const next = { ...current }
        delete next[item.id]
        return next
      })
      return
    }

    setEventsLoadingId(item.id)
    setError('')
    try {
      const events = await getAdminVerificationEvents(item.id)
      setEventsById((current) => ({ ...current, [item.id]: events }))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać historii weryfikacji.')
    } finally {
      setEventsLoadingId(null)
    }
  }

  return (
    <main className="admin-verifications-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Administracja</span>
          <h1>Weryfikacje tożsamości</h1>
          <p>Rozpatruj zgłoszenia, cofaj status i kontroluj pełną historię decyzji.</p>
        </div>
        <label className="verification-filter">
          <span>Status</span>
          <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
            <option value="PENDING">Oczekujące</option>
            <option value="VERIFIED">Zweryfikowane</option>
            <option value="REJECTED">Odrzucone</option>
            <option value="REVOKED">Cofnięte</option>
            <option value="">Wszystkie</option>
          </select>
        </label>
      </header>

      {error && <div className="form-message form-message--error">{error}</div>}
      {loading && <div className="page-state">Pobieranie weryfikacji…</div>}

      {!loading && data?.content.length === 0 && (
        <div className="page-state">Brak zgłoszeń w tej kategorii.</div>
      )}

      {!loading && data?.content.length > 0 && (
        <section className="verification-review-list">
          {data.content.map((item) => {
            const reason = reasonById[item.id] || ''
            const negativeDecisionReady = reason.trim().length >= 5
            return (
              <article className="panel verification-review" key={item.id}>
                <div className="verification-review__heading">
                  <div>
                    <span className="eyebrow">#{item.id} · {statusLabels[item.status] || item.status}</span>
                    <h2>{item.nickname}</h2>
                    <p>{item.email}</p>
                  </div>
                  <div className="verification-review__meta">
                    <span>{item.provider === 'MANUAL_REVIEW' ? 'Manual review' : item.provider}</span>
                    <time>{formatDate(item.requestedAt)}</time>
                  </div>
                </div>

                {item.decisionReason && (
                  <div className="verification-review__decision">
                    <strong>Powód ostatniej decyzji</strong>
                    <span>{item.decisionReason}</span>
                  </div>
                )}

                <div className="verification-review__actions">
                  {item.status === 'PENDING' && (
                    <button className="button button--primary" type="button" disabled={busyId === item.id} onClick={() => decide(item, 'APPROVE')}>
                      Zatwierdź
                    </button>
                  )}

                  {(item.status === 'PENDING' || item.status === 'VERIFIED') && (
                    <label className="verification-review__reason">
                      <span>{item.status === 'VERIFIED' ? 'Powód cofnięcia' : 'Powód odrzucenia'}</span>
                      <input
                        value={reason}
                        onChange={(event) => setReasonById((current) => ({ ...current, [item.id]: event.target.value }))}
                        minLength={5}
                        maxLength={500}
                        placeholder="Wymagane przy decyzji negatywnej"
                      />
                    </label>
                  )}

                  {item.status === 'PENDING' && (
                    <button className="button button--secondary" type="button" disabled={busyId === item.id || !negativeDecisionReady} onClick={() => decide(item, 'REJECT')}>
                      Odrzuć
                    </button>
                  )}
                  {item.status === 'VERIFIED' && (
                    <button className="button button--secondary" type="button" disabled={busyId === item.id || !negativeDecisionReady} onClick={() => decide(item, 'REVOKE')}>
                      Cofnij weryfikację
                    </button>
                  )}
                  <button className="button button--secondary" type="button" disabled={eventsLoadingId === item.id} onClick={() => toggleEvents(item)}>
                    {eventsById[item.id] ? 'Ukryj historię' : eventsLoadingId === item.id ? 'Pobieranie…' : 'Historia'}
                  </button>
                </div>

                {eventsById[item.id] && (
                  <div className="verification-audit">
                    {eventsById[item.id].map((event) => (
                      <div className="verification-audit__row" key={event.id}>
                        <div>
                          <strong>{eventLabels[event.eventType] || event.eventType}</strong>
                          <span>{event.actorNickname} · {formatDate(event.createdAt)}</span>
                        </div>
                        <span>{event.fromStatus || '—'} → {event.toStatus}</span>
                        {event.reason && <p>{event.reason}</p>}
                      </div>
                    ))}
                  </div>
                )}
              </article>
            )
          })}
        </section>
      )}

      {data && data.totalPages > 1 && (
        <div className="pagination">
          <button type="button" disabled={data.first || loading} onClick={() => setPage((current) => current - 1)}>Poprzednia</button>
          <span>{data.page + 1} / {data.totalPages}</span>
          <button type="button" disabled={data.last || loading} onClick={() => setPage((current) => current + 1)}>Następna</button>
        </div>
      )}
    </main>
  )
}

export default AdminVerificationsPage
