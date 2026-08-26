import { useCallback, useEffect, useState } from 'react'
import {
  approveJobCancellation,
  declineJobCancellation,
  getPendingJobCancellation,
  requestJobCancellation,
  withdrawJobCancellation,
} from '../api/jobsApi.js'
import './JobCancellationPanel.css'

const MAX_REASON_LENGTH = 1000

function JobCancellationPanel({ job, user, onJobChanged }) {
  const isCreator = job.createdById === user?.id
  const isWorker = job.takenById === user?.id
  const isParticipant = isCreator || isWorker
  const eligible = isParticipant && job.status === 'IN_PROGRESS' && Boolean(job.takenById)

  const [request, setRequest] = useState(null)
  const [reason, setReason] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const loadRequest = useCallback(async () => {
    if (!eligible) {
      setRequest(null)
      return
    }
    setLoading(true)
    setError('')
    try {
      setRequest(await getPendingJobCancellation(job.id))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać statusu anulowania.')
    } finally {
      setLoading(false)
    }
  }, [eligible, job.id])

  useEffect(() => {
    loadRequest()
  }, [loadRequest])

  if (!eligible) return null

  const requestedByMe = request?.requestedById === user?.id
  const normalizedReason = reason.trim()
  const canSubmit = normalizedReason.length >= 3 && normalizedReason.length <= MAX_REASON_LENGTH

  async function submitRequest(event) {
    event.preventDefault()
    if (!canSubmit || busy) return
    setBusy('request')
    setError('')
    setSuccess('')
    try {
      const created = await requestJobCancellation(job.id, normalizedReason)
      setRequest(created)
      setReason('')
      setFormOpen(false)
      setSuccess('Prośba została wysłana. Zlecenie pozostaje aktywne do decyzji drugiej strony.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wysłać prośby o anulowanie.')
    } finally {
      setBusy('')
    }
  }

  async function resolve(action, successMessage) {
    if (busy) return
    setBusy(action)
    setError('')
    setSuccess('')
    try {
      if (action === 'approve') await approveJobCancellation(job.id)
      if (action === 'decline') await declineJobCancellation(job.id)
      if (action === 'withdraw') await withdrawJobCancellation(job.id)
      await loadRequest()
      if (action === 'approve') await onJobChanged?.()
      setSuccess(successMessage)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zapisać decyzji.')
    } finally {
      setBusy('')
    }
  }

  return (
    <section className="job-cancellation-panel" aria-busy={Boolean(busy) || loading}>
      <div className="job-cancellation-panel__header">
        <div>
          <span className="eyebrow">Anulowanie aktywnego zlecenia</span>
          <h2>Decyzja obu stron</h2>
        </div>
        <span className="job-cancellation-panel__badge">Escrow chronione</span>
      </div>

      <p className="job-cancellation-panel__intro">
        Po przyjęciu zlecenia żadna strona nie może anulować go jednostronnie. Zgoda drugiej strony kończy realizację,
        wyłącza udostępnianie lokalizacji i zwraca pełne środki escrow zlecającemu.
      </p>

      {error && <div className="form-message form-message--error" role="alert">{error}</div>}
      {success && <div className="form-message form-message--success" role="status">{success}</div>}

      {loading && !request && <p className="job-cancellation-panel__muted">Sprawdzanie aktywnej prośby…</p>}

      {!loading && !request && !formOpen && (
        <div className="job-cancellation-panel__empty">
          <p>Jeśli obie strony zgadzają się przerwać realizację, rozpocznij bezpieczny proces anulowania.</p>
          <button className="button button--secondary" type="button" onClick={() => setFormOpen(true)}>
            Poproś o anulowanie
          </button>
        </div>
      )}

      {!request && formOpen && (
        <form className="job-cancellation-panel__form" onSubmit={submitRequest}>
          <label htmlFor="cancellation-reason">Powód anulowania</label>
          <textarea
            id="cancellation-reason"
            rows="4"
            maxLength={MAX_REASON_LENGTH}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Krótko wyjaśnij drugiej stronie, dlaczego chcesz zakończyć realizację."
            disabled={Boolean(busy)}
          />
          <div className="job-cancellation-panel__form-footer">
            <span>{reason.length}/{MAX_REASON_LENGTH}</span>
            <div className="job-cancellation-panel__actions">
              <button className="button button--secondary" type="button" onClick={() => setFormOpen(false)} disabled={Boolean(busy)}>
                Wróć
              </button>
              <button className="button button--danger" type="submit" disabled={!canSubmit || Boolean(busy)}>
                {busy === 'request' ? 'Wysyłanie…' : 'Wyślij prośbę'}
              </button>
            </div>
          </div>
        </form>
      )}

      {request && (
        <div className="job-cancellation-panel__request">
          <div className="job-cancellation-panel__request-meta">
            <strong>{requestedByMe ? 'Czekasz na decyzję drugiej strony' : 'Druga strona prosi o anulowanie'}</strong>
            <span>{request.requestedAt ? new Date(request.requestedAt).toLocaleString('pl-PL') : 'Oczekuje na decyzję'}</span>
          </div>
          <blockquote>{request.reason}</blockquote>

          {requestedByMe ? (
            <div className="job-cancellation-panel__decision">
              <p>Zlecenie nadal jest aktywne. Możesz wycofać prośbę dopóki druga strona jej nie rozpatrzy.</p>
              <button
                className="button button--secondary"
                type="button"
                disabled={Boolean(busy)}
                onClick={() => resolve('withdraw', 'Prośba o anulowanie została wycofana.')}
              >
                {busy === 'withdraw' ? 'Wycofywanie…' : 'Wycofaj prośbę'}
              </button>
            </div>
          ) : (
            <div className="job-cancellation-panel__decision">
              <p>
                Akceptacja jest ostateczna: zlecenie przejdzie do anulowanych, tracking zostanie zatrzymany,
                a środki escrow wrócą do zlecającego. Jeśli problem dotyczy wykonania lub szkody, wybierz spór zamiast anulowania.
              </p>
              <div className="job-cancellation-panel__actions">
                <button
                  className="button button--secondary"
                  type="button"
                  disabled={Boolean(busy)}
                  onClick={() => resolve('decline', 'Prośba została odrzucona. Realizacja pozostaje aktywna.')}
                >
                  {busy === 'decline' ? 'Odrzucanie…' : 'Odrzuć'}
                </button>
                <button
                  className="button button--danger"
                  type="button"
                  disabled={Boolean(busy)}
                  onClick={() => resolve('approve', 'Zlecenie zostało anulowane, a escrow zwrócone zlecającemu.')}
                >
                  {busy === 'approve' ? 'Anulowanie…' : 'Zgadzam się — anuluj'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

export default JobCancellationPanel
