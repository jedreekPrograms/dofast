import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext.js'
import {
  claimAdminDispute,
  getAdminDispute,
  getAdminDisputeMessages,
  getAdminDisputes,
  resolveAdminDispute,
} from '../api/adminApi.js'
import './AdminDisputesPage.css'

const STATUS_LABELS = {
  OPEN: 'Otwarte',
  UNDER_REVIEW: 'W analizie',
  RESOLVED: 'Rozstrzygnięte',
  CANCELLED: 'Anulowane',
}

const RESOLUTION_LABELS = {
  RELEASE_TO_WORKER: 'Wypłać wykonawcy',
  REFUND_TO_REQUESTER: 'Zwróć zlecającemu',
  RESUME_JOB: 'Wznów zlecenie',
}

function AdminDisputesPage() {
  const { user } = useAuth()
  const [pageData, setPageData] = useState(null)
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState(null)
  const [evidence, setEvidence] = useState(null)
  const [resolution, setResolution] = useState('RESUME_JOB')
  const [note, setNote] = useState('')
  const [expenseMode, setExpenseMode] = useState('DEFAULT')
  const [approvedExpenseAmount, setApprovedExpenseAmount] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [evidenceLoading, setEvidenceLoading] = useState(false)
  const [error, setError] = useState('')

  const loadQueue = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setPageData(await getAdminDisputes({ status, page, size: 20 }))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać kolejki sporów.')
    } finally {
      setLoading(false)
    }
  }, [page, status])

  useEffect(() => {
    loadQueue()
  }, [loadQueue])

  async function openDetails(disputeId) {
    setBusy(true)
    setError('')
    try {
      const [detail, messages] = await Promise.all([
        getAdminDispute(disputeId),
        getAdminDisputeMessages(disputeId),
      ])
      setSelected(detail)
      setEvidence(messages)
      setNote('')
      setResolution('RESUME_JOB')
      setExpenseMode('DEFAULT')
      setApprovedExpenseAmount('')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać sprawy.')
    } finally {
      setBusy(false)
    }
  }

  async function loadOlderEvidence() {
    if (!selected || !evidence?.hasMore || !evidence.nextBeforeId) return
    setEvidenceLoading(true)
    setError('')
    try {
      const older = await getAdminDisputeMessages(selected.dispute.id, {
        beforeId: evidence.nextBeforeId,
        limit: 100,
      })
      setEvidence((current) => ({
        messages: [...older.messages, ...(current?.messages ?? [])],
        nextBeforeId: older.nextBeforeId,
        hasMore: older.hasMore,
      }))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać starszych wiadomości.')
    } finally {
      setEvidenceLoading(false)
    }
  }

  async function claim() {
    if (!selected) return
    setBusy(true)
    setError('')
    try {
      const detail = await claimAdminDispute(selected.dispute.id)
      setSelected(detail)
      await loadQueue()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się przypisać sprawy.')
    } finally {
      setBusy(false)
    }
  }

  function changeResolution(nextResolution) {
    setResolution(nextResolution)
    if (nextResolution === 'RESUME_JOB') {
      setExpenseMode('DEFAULT')
      setApprovedExpenseAmount('')
    }
  }

  function expenseAmountForRequest() {
    if (resolution === 'RESUME_JOB' || expenseMode === 'DEFAULT') return null
    const normalized = approvedExpenseAmount.trim().replace(',', '.')
    if (!/^\d{1,5}(\.\d{1,2})?$/.test(normalized)) {
      throw new Error('Podaj zatwierdzoną kwotę wydatków od 0,00 do 99 999,99 PLN, maksymalnie z dwoma miejscami po przecinku.')
    }
    const amount = Number(normalized)
    if (!Number.isFinite(amount) || amount < 0 || amount > 99999.99) {
      throw new Error('Zatwierdzona kwota wydatków jest poza dozwolonym zakresem.')
    }
    return amount
  }

  async function resolve(event) {
    event.preventDefault()
    if (!selected) return
    setBusy(true)
    setError('')
    try {
      const expenseAmount = expenseAmountForRequest()
      const detail = await resolveAdminDispute(selected.dispute.id, resolution, note, expenseAmount)
      setSelected(detail)
      setNote('')
      setExpenseMode('DEFAULT')
      setApprovedExpenseAmount('')
      await loadQueue()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się rozstrzygnąć sprawy.')
    } finally {
      setBusy(false)
    }
  }

  const canResolve = selected
    && ['OPEN', 'UNDER_REVIEW'].includes(selected.dispute.status)
    && (!selected.dispute.assignedAdminId || selected.dispute.assignedAdminId === user.id)

  const defaultExpenseLabel = resolution === 'RELEASE_TO_WORKER'
    ? 'Domyślnie: zwróć wszystkie zgłoszone wydatki wykonawcy'
    : 'Domyślnie: nie zwracaj wydatków wykonawcy'

  return (
    <main className="admin-disputes-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Administracja · escrow</span>
          <h1>Spory i rozstrzygnięcia</h1>
          <p>Każda decyzja zmienia status zlecenia i escrow w jednej transakcji oraz zapisuje się w audycie.</p>
        </div>
        <label className="admin-disputes-filter">
          Status
          <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
            <option value="">Wszystkie</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
      </header>

      {error && <div className="form-message form-message--error">{error}</div>}

      <div className="admin-disputes-layout">
        <section className="panel admin-disputes-queue">
          <div className="admin-disputes-queue__heading">
            <div><span className="eyebrow">Kolejka</span><h2>Sprawy</h2></div>
            {pageData && <strong>{pageData.totalElements}</strong>}
          </div>
          {loading && <div className="page-state">Pobieranie spraw…</div>}
          {!loading && pageData?.content.length === 0 && <div className="page-state">Brak spraw dla wybranego filtra.</div>}
          <div className="admin-dispute-list">
            {pageData?.content.map((dispute) => (
              <button className="admin-dispute-row" type="button" key={dispute.id} onClick={() => openDetails(dispute.id)}>
                <div>
                  <strong>#{dispute.id} · {dispute.jobTitle}</strong>
                  <span>Zlecenie #{dispute.jobId} · zgłaszający #{dispute.openedById}</span>
                </div>
                <div className="admin-dispute-row__status">
                  <span className={`status-pill status-pill--${dispute.status.toLowerCase()}`}>{STATUS_LABELS[dispute.status] || dispute.status}</span>
                  {dispute.assignedAdminId && <small>admin #{dispute.assignedAdminId}</small>}
                </div>
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

        <section className="panel admin-dispute-detail">
          {!selected && <div className="page-state">Wybierz sprawę z kolejki, aby zobaczyć szczegóły.</div>}
          {selected && (
            <>
              <div className="admin-dispute-detail__heading">
                <div><span className="eyebrow">Sprawa #{selected.dispute.id}</span><h2>{selected.dispute.jobTitle}</h2></div>
                <span className={`status-pill status-pill--${selected.dispute.status.toLowerCase()}`}>{STATUS_LABELS[selected.dispute.status]}</span>
              </div>

              <div className="admin-dispute-facts">
                <div><span>Zlecający</span><strong>#{selected.dispute.requesterId}</strong></div>
                <div><span>Wykonawca</span><strong>#{selected.dispute.workerId}</strong></div>
                <div><span>Powód</span><strong>{selected.dispute.reason}</strong></div>
                <div><span>Stan przed sporem</span><strong>{selected.dispute.previousJobStatus}</strong></div>
              </div>

              <div className="admin-dispute-description"><strong>Opis zgłoszenia</strong><p>{selected.dispute.description}</p></div>

              <section className="admin-evidence">
                <div className="admin-evidence__heading">
                  <div><h3>Dowody z czatu</h3><p>Tylko wiadomości z zlecenia powiązanego z tą sprawą.</p></div>
                  {evidence?.hasMore && (
                    <button className="button button--secondary" type="button" disabled={evidenceLoading} onClick={loadOlderEvidence}>
                      {evidenceLoading ? 'Pobieranie…' : 'Starsze wiadomości'}
                    </button>
                  )}
                </div>
                {evidence?.messages.length === 0 && <div className="page-state">Brak wiadomości do analizy.</div>}
                <div className="admin-evidence__messages">
                  {evidence?.messages.map((message) => (
                    <article className="admin-evidence-message" key={message.id}>
                      <div><strong>{message.senderNickname}</strong><span>użytkownik #{message.senderId}</span></div>
                      <p>{message.content}</p>
                      <time>{new Date(message.createdAt).toLocaleString('pl-PL')}</time>
                    </article>
                  ))}
                </div>
              </section>

              {selected.dispute.status === 'OPEN' && !selected.dispute.assignedAdminId && (
                <button className="button button--secondary" type="button" disabled={busy} onClick={claim}>Przypisz sprawę do mnie</button>
              )}

              {selected.dispute.assignedAdminId && selected.dispute.assignedAdminId !== user.id && ['OPEN', 'UNDER_REVIEW'].includes(selected.dispute.status) && (
                <div className="form-message">Sprawę prowadzi administrator #{selected.dispute.assignedAdminId}.</div>
              )}

              {canResolve && (
                <form className="admin-resolution" onSubmit={resolve}>
                  <h3>Rozstrzygnięcie</h3>
                  <label>
                    Decyzja
                    <select value={resolution} onChange={(event) => changeResolution(event.target.value)}>
                      {Object.entries(RESOLUTION_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                  </label>
                  {resolution !== 'RESUME_JOB' && (
                    <>
                      <label>
                        Rozliczenie udokumentowanych wydatków
                        <select value={expenseMode} onChange={(event) => setExpenseMode(event.target.value)}>
                          <option value="DEFAULT">{defaultExpenseLabel}</option>
                          <option value="EXACT">Ustal dokładną zatwierdzoną kwotę</option>
                        </select>
                      </label>
                      {expenseMode === 'EXACT' && (
                        <label>
                          Zatwierdzona kwota wydatków (PLN)
                          <input
                            type="text"
                            inputMode="decimal"
                            value={approvedExpenseAmount}
                            onChange={(event) => setApprovedExpenseAmount(event.target.value)}
                            placeholder="np. 35,00"
                            required
                          />
                          <small>Kwota nie może przekroczyć sumy zgłoszonych wydatków. Reszta budżetu wraca do zlecającego.</small>
                        </label>
                      )}
                    </>
                  )}
                  <label>
                    Uzasadnienie
                    <textarea value={note} onChange={(event) => setNote(event.target.value)} maxLength={4000} minLength={3} rows={5} required placeholder="Opisz podstawę decyzji. Tekst trafi do historii sprawy." />
                  </label>
                  <button className="button button--primary" type="submit" disabled={busy}>Zatwierdź decyzję</button>
                </form>
              )}

              {selected.dispute.resolution && (
                <div className="admin-resolution-result">
                  <strong>{RESOLUTION_LABELS[selected.dispute.resolution] || selected.dispute.resolution}</strong>
                  <p>{selected.dispute.adminNote}</p>
                </div>
              )}

              <div className="admin-audit">
                <h3>Audit trail</h3>
                {selected.events.map((event) => (
                  <div className="admin-audit-event" key={event.id}>
                    <div><strong>{event.eventType}</strong><span>{event.actorNickname} · #{event.actorId}</span></div>
                    {event.note && <p>{event.note}</p>}
                    <time>{new Date(event.createdAt).toLocaleString('pl-PL')}</time>
                  </div>
                ))}
              </div>
            </>
          )}
        </section>
      </div>
    </main>
  )
}

export default AdminDisputesPage
