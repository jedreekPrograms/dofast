import { useEffect, useState } from 'react'
import {
  failAdminPayout,
  getAdminPayoutEvents,
  getAdminPayouts,
  retryAdminPayout,
} from '../api/adminApi.js'
import './AdminPayoutsPage.css'

const STATUS_LABELS = {
  REQUESTED: 'Oczekuje',
  PROCESSING: 'Przetwarzana',
  REVIEW_REQUIRED: 'Wymaga weryfikacji',
  PAID: 'Wypłacona',
  FAILED: 'Nieudana',
  CANCELLED: 'Anulowana',
}

const EVENT_LABELS = {
  REQUESTED: 'Zlecono wypłatę',
  PROCESSING: 'Rozpoczęto przetwarzanie',
  PROVIDER_ACCEPTED: 'Provider przyjął wypłatę',
  PAID: 'Wypłata zakończona',
  REVIEW_REQUIRED: 'Skierowano do weryfikacji',
  FAILED: 'Wypłata zakończona błędem',
  CANCELLED: 'Wypłata anulowana',
  ADMIN_RETRY: 'Administrator zezwolił na retry',
  FUNDS_RESTORED: 'Środki przywrócono do portfela',
}

function money(value, currency = 'PLN') {
  return Number(value || 0).toLocaleString('pl-PL', { style: 'currency', currency })
}

function dateTime(value) {
  return value ? new Date(value).toLocaleString('pl-PL') : '—'
}

function AdminPayoutsPage() {
  const [status, setStatus] = useState('REVIEW_REQUIRED')
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
    getAdminPayouts({ status, page, size: 20 })
      .then((response) => { if (active) setData(response) })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać wypłat.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [status, page])

  function replaceOrRemove(updated) {
    setData((current) => {
      if (!current) return current
      if (!status || updated.status === status) {
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
    setEventsById((current) => {
      const next = { ...current }
      delete next[updated.id]
      return next
    })
  }

  async function retry(item) {
    setBusyId(item.id)
    setError('')
    try {
      replaceOrRemove(await retryAdminPayout(item.id))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się ponowić wypłaty.')
    } finally {
      setBusyId(null)
    }
  }

  async function fail(item) {
    const reason = (reasonById[item.id] || '').trim()
    if (reason.length < 5) {
      setError('Podaj konkretny powód odrzucenia wypłaty (minimum 5 znaków).')
      return
    }
    setBusyId(item.id)
    setError('')
    try {
      replaceOrRemove(await failAdminPayout(item.id, reason))
      setReasonById((current) => ({ ...current, [item.id]: '' }))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się definitywnie odrzucić wypłaty.')
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
      const events = await getAdminPayoutEvents(item.id)
      setEventsById((current) => ({ ...current, [item.id]: events }))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać audytu wypłaty.')
    } finally {
      setEventsLoadingId(null)
    }
  }

  return (
    <main className="admin-payouts-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Administracja · finanse</span>
          <h1>Operacje wypłat</h1>
          <p>Obsługuj wyłącznie wypłaty wymagające ręcznej decyzji. Retry zachowuje ten sam klucz idempotencji providera, a definitywne odrzucenie zwraca zarezerwowane środki do portfela przez ledger backendu.</p>
        </div>
        <label className="payout-filter">
          <span>Status</span>
          <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
            <option value="REVIEW_REQUIRED">Wymagają weryfikacji</option>
            <option value="REQUESTED">Oczekujące</option>
            <option value="PROCESSING">Przetwarzane</option>
            <option value="PAID">Wypłacone</option>
            <option value="FAILED">Nieudane</option>
            <option value="CANCELLED">Anulowane</option>
            <option value="">Wszystkie</option>
          </select>
        </label>
      </header>

      {error && <div className="form-message form-message--error">{error}</div>}
      {loading && <div className="page-state">Pobieranie operacji wypłat…</div>}
      {!loading && data?.content.length === 0 && <div className="page-state">Brak wypłat w tej kategorii.</div>}

      {!loading && data?.content.length > 0 && (
        <section className="payout-review-list">
          {data.content.map((item) => {
            const reviewRequired = item.status === 'REVIEW_REQUIRED'
            const reason = reasonById[item.id] || ''
            return (
              <article className="panel payout-review" key={item.id}>
                <div className="payout-review__heading">
                  <div>
                    <span className="eyebrow">#{item.id} · {STATUS_LABELS[item.status] || item.status}</span>
                    <h2>{money(item.amount, item.currency)}</h2>
                    <p>{item.userNickname} · użytkownik #{item.userId}</p>
                  </div>
                  <div className="payout-review__meta">
                    <span>Provider: {item.providerCode || '—'}</span>
                    <span>Próby: {item.attemptCount}</span>
                    <time>{dateTime(item.requestedAt)}</time>
                  </div>
                </div>

                <div className="payout-review__facts">
                  <div><span>Referencja providera</span><strong>{item.providerReference || '—'}</strong></div>
                  <div><span>Kod błędu</span><strong>{item.failureCode || '—'}</strong></div>
                  <div><span>Start przetwarzania</span><strong>{dateTime(item.processingStartedAt)}</strong></div>
                  <div><span>Rozstrzygnięta</span><strong>{dateTime(item.resolvedAt)}</strong></div>
                </div>

                {reviewRequired && (
                  <div className="payout-review__decision">
                    <div className="payout-review__warning">
                      Ta decyzja wpływa na środki użytkownika. Retry jest dozwolony tylko wtedy, gdy provider jest dostępny. Odrzucenie jest końcowe i backend przywraca środki do walletu idempotentną operacją ledgerową.
                    </div>
                    <label className="payout-review__reason">
                      <span>Powód definitywnego odrzucenia</span>
                      <textarea
                        rows="3"
                        minLength="5"
                        maxLength="1000"
                        value={reason}
                        onChange={(event) => setReasonById((current) => ({ ...current, [item.id]: event.target.value }))}
                        placeholder="Wymagany do trwałego audytu decyzji"
                      />
                    </label>
                    <div className="payout-review__actions">
                      <button className="button button--secondary" type="button" disabled={busyId === item.id} onClick={() => retry(item)}>
                        {busyId === item.id ? 'Zapisywanie…' : 'Zezwól na retry'}
                      </button>
                      <button className="button button--primary" type="button" disabled={busyId === item.id || reason.trim().length < 5} onClick={() => fail(item)}>
                        {busyId === item.id ? 'Zapisywanie…' : 'Odrzuć i zwróć środki'}
                      </button>
                    </div>
                  </div>
                )}

                <button className="button button--ghost" type="button" disabled={eventsLoadingId === item.id} onClick={() => toggleEvents(item)}>
                  {eventsById[item.id] ? 'Ukryj audyt' : eventsLoadingId === item.id ? 'Pobieranie…' : 'Pokaż pełny audyt'}
                </button>

                {eventsById[item.id] && (
                  <div className="payout-audit">
                    {eventsById[item.id].map((event) => (
                      <div className="payout-audit__row" key={event.id}>
                        <div>
                          <strong>{EVENT_LABELS[event.eventType] || event.eventType}</strong>
                          <span>{event.source} · {dateTime(event.createdAt)}</span>
                        </div>
                        <div>
                          <span>{event.actorNickname ? `Operator: ${event.actorNickname}` : 'Zdarzenie systemowe'}</span>
                          {event.note && <p>{event.note}</p>}
                        </div>
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

export default AdminPayoutsPage
