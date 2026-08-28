import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import ReviewDialog from '../../reviews/components/ReviewDialog.jsx'
import UserTrustCard from '../../reviews/components/UserTrustCard.jsx'
import JobAttachmentPanel from '../components/JobAttachmentPanel.jsx'
import JobProposalPanel from '../components/JobProposalPanel.jsx'
import {
  acceptJob,
  approveJobCancellation,
  cancelJob,
  confirmJobCompletion,
  declineJobCancellation,
  getJob,
  getPendingJobCancellation,
  requestJobCancellation,
  requestJobCompletion,
  withdrawJobCancellation,
} from '../api/jobsApi.js'
import './JobDetailsPage.css'

const STATUS_LABELS = {
  OPEN: 'Otwarte',
  IN_PROGRESS: 'W realizacji',
  COMPLETION_REQUESTED: 'Do potwierdzenia',
  DISPUTED: 'W sporze',
  DONE: 'Zakończone',
  CANCELLED: 'Anulowane',
}

const STATUS_DESCRIPTIONS = {
  OPEN: 'Zlecenie czeka na wykonawcę. Dokładne punkty A i B są ukryte przed osobami postronnymi.',
  IN_PROGRESS: 'Wykonawca przyjął zlecenie i może korzystać z trasy oraz bieżącego śledzenia przejazdu.',
  COMPLETION_REQUESTED: 'Wykonawca zgłosił wykonanie. Zlecający powinien potwierdzić zakończenie albo otworzyć spór.',
  DISPUTED: 'Realizacja została wstrzymana przez spór. Precyzyjna lokalizacja kuriera nie jest już udostępniana.',
  DONE: 'Zlecenie zostało zakończone. Obie strony mogą wystawić sobie opinię.',
  CANCELLED: 'Zlecenie zostało anulowane i nie może już zostać przyjęte.',
}

const WORKER_ROUTE_STATUSES = new Set(['IN_PROGRESS', 'COMPLETION_REQUESTED', 'DISPUTED'])
const DISPUTABLE_STATUSES = new Set(['IN_PROGRESS', 'COMPLETION_REQUESTED'])

const priceFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

const dateFormatter = new Intl.DateTimeFormat('pl-PL', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function JobDetailsPage() {
  const { jobId } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [job, setJob] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [busyAction, setBusyAction] = useState('')
  const [reviewOpen, setReviewOpen] = useState(false)
  const [reviewed, setReviewed] = useState(false)
  const [cancellation, setCancellation] = useState(null)
  const [cancellationReason, setCancellationReason] = useState('')

  const loadJob = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setJob(await getJob(jobId))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać szczegółów zlecenia.')
    } finally {
      setLoading(false)
    }
  }, [jobId])

  const loadCancellation = useCallback(async () => {
    if (!user?.id) {
      setCancellation(null)
      return
    }
    try {
      setCancellation(await getPendingJobCancellation(jobId))
    } catch (requestError) {
      if (requestError.status === 403 || requestError.status === 404 || requestError.status === 409) {
        setCancellation(null)
        return
      }
      setError(requestError.message || 'Nie udało się pobrać stanu anulowania zlecenia.')
    }
  }, [jobId, user?.id])

  useEffect(() => {
    loadJob()
  }, [loadJob])

  useEffect(() => {
    if (job?.status === 'IN_PROGRESS' && (job.createdById === user?.id || job.takenById === user?.id)) {
      loadCancellation()
    } else {
      setCancellation(null)
    }
  }, [job, loadCancellation, user?.id])

  async function runAction(actionName, action, successMessage) {
    setBusyAction(actionName)
    setError('')
    setSuccess('')
    try {
      const updated = await action(job.id)
      setJob(updated)
      setSuccess(successMessage)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wykonać operacji.')
    } finally {
      setBusyAction('')
    }
  }

  async function runCancellationAction(actionName, action, successMessage) {
    setBusyAction(actionName)
    setError('')
    setSuccess('')
    try {
      await action(job.id)
      setSuccess(successMessage)
      await Promise.all([loadJob(), loadCancellation()])
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zaktualizować prośby o anulowanie.')
    } finally {
      setBusyAction('')
    }
  }

  async function handleRequestCancellation(event) {
    event.preventDefault()
    const reason = cancellationReason.trim()
    if (!reason) {
      setError('Podaj krótki powód prośby o anulowanie.')
      return
    }

    setBusyAction('cancellation-request')
    setError('')
    setSuccess('')
    try {
      const created = await requestJobCancellation(job.id, reason)
      setCancellation(created)
      setCancellationReason('')
      setSuccess('Prośba o anulowanie została wysłana do drugiej strony.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wysłać prośby o anulowanie.')
    } finally {
      setBusyAction('')
    }
  }

  async function handleAccept() {
    setBusyAction('accept')
    setError('')
    setSuccess('')
    try {
      const updated = await acceptJob(job.id)
      setJob(updated)
      navigate(`/jobs/${updated.id}/route`)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się przyjąć zlecenia.')
      setBusyAction('')
    }
  }

  function handleReviewSubmitted() {
    setReviewed(true)
    setReviewOpen(false)
    setSuccess('Opinia została zapisana i jest już widoczna na profilu użytkownika.')
  }

  if (loading) {
    return <main><div className="page-state">Pobieranie szczegółów zlecenia…</div></main>
  }

  if (!job) {
    return (
      <main className="job-details-page">
        <div className="page-state">
          <p>{error || 'Nie znaleziono zlecenia.'}</p>
          <Link className="button button--secondary" to="/">Wróć do zleceń</Link>
        </div>
      </main>
    )
  }

  const isCreator = job.createdById === user?.id
  const isWorker = job.takenById === user?.id
  const isParticipant = isCreator || isWorker
  const proposalMode = job.assignmentMode === 'PROPOSALS'
  const canAccept = job.status === 'OPEN' && !isCreator && !proposalMode
  const showProposalPanel = proposalMode && (isCreator || isWorker || job.status === 'OPEN')
  const canOpenRoute = Boolean(job.destinationLabel) && (
    isCreator || (isWorker && WORKER_ROUTE_STATUSES.has(job.status))
  )
  const canOpenDispute = isParticipant && DISPUTABLE_STATUSES.has(job.status)
  const canReview = isParticipant && job.status === 'DONE'
  const canNegotiateCancellation = isParticipant && job.status === 'IN_PROGRESS'
  const cancellationRequestedByMe = cancellation?.requestedById === user?.id

  return (
    <main className="job-details-page">
      <div className="job-details-page__back-row">
        <Link to="/">← Wszystkie zlecenia</Link>
        <span>#{job.id}</span>
      </div>

      <section className="job-details-hero">
        <div className="job-details-hero__content">
          <div className="job-details-hero__meta">
            <span className={`status-pill status-pill--${job.status.toLowerCase()}`}>
              {STATUS_LABELS[job.status] || job.status}
            </span>
            <span className="job-details-assignment-pill">
              {proposalMode ? 'Zlecający wybiera wykonawcę' : 'Kto pierwszy, ten bierze'}
            </span>
            {proposalMode && job.priceNegotiationEnabled && <span className="job-details-assignment-pill">Cena do negocjacji</span>}
            <span>{job.createdAt ? `Opublikowano ${dateFormatter.format(new Date(job.createdAt))}` : 'Opublikowane zlecenie'}</span>
          </div>
          <h1>{job.title}</h1>
          <p className="job-details-hero__description">{job.description}</p>
          <div className="job-details-route-summary" aria-label="Podsumowanie trasy">
            <div>
              <span>Punkt A</span>
              <strong>{job.locationLabel || 'Lokalizacja do ustalenia'}</strong>
            </div>
            <span className="job-details-route-summary__arrow">→</span>
            <div>
              <span>Punkt B</span>
              <strong>{job.destinationLabel || 'Cel do ustalenia'}</strong>
            </div>
          </div>
        </div>

        <aside className="job-details-price-card">
          <span>{proposalMode ? 'Budżet / wynagrodzenie' : 'Wynagrodzenie'}</span>
          <strong>{priceFormatter.format(Number(job.price))}</strong>
          {proposalMode && (
            <small className="job-details-price-card__hint">
              {job.status === 'OPEN'
                ? (job.priceNegotiationEnabled ? 'Finalna cena zostanie ustalona po wyborze propozycji.' : 'Cena jest stała; zlecający wybiera tylko wykonawcę.')
                : 'To finalna cena zaakceptowanej propozycji.'}
            </small>
          )}
          {(job.routeDistanceMeters || job.routeDurationSeconds) && (
            <div className="job-details-price-card__route">
              {job.routeDistanceMeters && <span>{formatDistance(job.routeDistanceMeters)}</span>}
              {job.routeDurationSeconds && <span>około {formatDuration(job.routeDurationSeconds)}</span>}
            </div>
          )}
        </aside>
      </section>

      {error && <div className="form-message form-message--error" role="alert">{error}</div>}
      {success && <div className="form-message form-message--success" role="status">{success}</div>}

      <div className="job-details-grid">
        <section className="job-details-panel">
          <span className="eyebrow">Stan realizacji</span>
          <h2>{STATUS_LABELS[job.status] || job.status}</h2>
          <p>{STATUS_DESCRIPTIONS[job.status] || 'Status zlecenia został zaktualizowany.'}</p>

          {isCreator && (
            <div className="job-details-role-note">
              <strong>Jesteś zlecającym.</strong>{' '}
              {proposalMode && job.status === 'OPEN'
                ? 'Kandydaci mogą wysyłać prywatne propozycje. Dopiero Twój wybór przypisze wykonawcę.'
                : 'Zarządzasz tym zleceniem i potwierdzasz jego wykonanie.'}
            </div>
          )}
          {isWorker && <div className="job-details-role-note"><strong>Realizujesz to zlecenie.</strong> Masz dostęp do danych wykonawczych potrzebnych do realizacji.</div>}
          {!isParticipant && job.status === 'OPEN' && (
            <div className="job-details-role-note">
              <strong>Zlecenie jest dostępne.</strong>{' '}
              {proposalMode
                ? 'Wyślij prywatną propozycję. Dokładne dane wykonawcze otrzymasz dopiero, jeśli zlecający wybierze Ciebie.'
                : 'Po przyjęciu otrzymasz dostęp do dokładnej trasy A → B.'}
            </div>
          )}

          <div className="job-details-actions" aria-busy={Boolean(busyAction)}>
            {canAccept && (
              <button className="button button--primary" type="button" disabled={Boolean(busyAction)} onClick={handleAccept}>
                {busyAction === 'accept' ? 'Przyjmowanie…' : 'Przyjmij zlecenie'}
              </button>
            )}
            {canOpenRoute && <Link className="button button--secondary" to={`/jobs/${job.id}/route`}>Trasa A → B</Link>}
            {isParticipant && job.takenById && <Link className="button button--secondary" to={`/chat?jobId=${job.id}`}>Otwórz czat</Link>}
            {isCreator && job.status === 'OPEN' && (
              <button className="button button--danger" type="button" disabled={Boolean(busyAction)} onClick={() => runAction('cancel', cancelJob, 'Zlecenie zostało anulowane.')}>
                {busyAction === 'cancel' ? 'Anulowanie…' : 'Anuluj zlecenie'}
              </button>
            )}
            {isWorker && job.status === 'IN_PROGRESS' && (
              <button className="button button--primary" type="button" disabled={Boolean(busyAction)} onClick={() => runAction('completion', requestJobCompletion, 'Zgłoszono wykonanie. Zlecający może teraz je potwierdzić.')}>
                {busyAction === 'completion' ? 'Zapisywanie…' : 'Zgłoś wykonanie'}
              </button>
            )}
            {isCreator && job.status === 'COMPLETION_REQUESTED' && (
              <button className="button button--primary" type="button" disabled={Boolean(busyAction)} onClick={() => runAction('confirm', confirmJobCompletion, 'Wykonanie zostało potwierdzone.')}>
                {busyAction === 'confirm' ? 'Potwierdzanie…' : 'Potwierdź wykonanie'}
              </button>
            )}
            {canOpenDispute && <Link className="button button--secondary" to={`/disputes?jobId=${job.id}`}>Otwórz spór</Link>}
            {isParticipant && job.status === 'DISPUTED' && <Link className="button button--secondary" to="/disputes">Zobacz spór</Link>}
            {canReview && (
              <button className="button button--secondary" type="button" disabled={reviewed} onClick={() => setReviewOpen(true)}>
                {reviewed ? 'Oceniono' : 'Oceń współpracę'}
              </button>
            )}
          </div>
        </section>

        <section className="job-details-panel">
          <span className="eyebrow">Uczestnicy i zaufanie</span>
          <div className="marketplace-trust-grid">
            <UserTrustCard userId={job.createdById} roleLabel="Zlecający" />
            <UserTrustCard userId={job.takenById} roleLabel="Wykonawca" />
          </div>
        </section>

        <JobAttachmentPanel
          job={job}
          currentUserId={user?.id}
          onSuccess={(message) => {
            setError('')
            setSuccess(message)
          }}
        />

        {showProposalPanel && (
          <JobProposalPanel
            job={job}
            currentUserId={user?.id}
            onJobUpdated={setJob}
            onSuccess={(message) => {
              setError('')
              setSuccess(message)
            }}
          />
        )}

        {canNegotiateCancellation && (
          <section className="job-details-panel job-details-panel--cancellation">
            <span className="eyebrow">Anulowanie za zgodą stron</span>
            <h2>{cancellation ? 'Oczekująca prośba' : 'Potrzebujesz anulować aktywne zlecenie?'}</h2>
            {cancellation ? (
              <>
                <p>{cancellation.reason}</p>
                <div className="job-details-cancellation__meta">
                  <span>Wysłano {dateFormatter.format(new Date(cancellation.requestedAt))}</span>
                  <span>{cancellationRequestedByMe ? 'Czeka na decyzję drugiej strony.' : 'Twoja decyzja jest wymagana.'}</span>
                </div>
                <div className="job-details-actions">
                  {cancellationRequestedByMe ? (
                    <button
                      className="button button--secondary"
                      type="button"
                      disabled={Boolean(busyAction)}
                      onClick={() => runCancellationAction('cancellation-withdraw', withdrawJobCancellation, 'Prośba o anulowanie została wycofana.')}
                    >
                      {busyAction === 'cancellation-withdraw' ? 'Wycofywanie…' : 'Wycofaj prośbę'}
                    </button>
                  ) : (
                    <>
                      <button
                        className="button button--danger"
                        type="button"
                        disabled={Boolean(busyAction)}
                        onClick={() => runCancellationAction('cancellation-approve', approveJobCancellation, 'Anulowanie zostało zaakceptowane. Środki wracają do zlecającego.')}
                      >
                        {busyAction === 'cancellation-approve' ? 'Anulowanie…' : 'Zaakceptuj anulowanie'}
                      </button>
                      <button
                        className="button button--secondary"
                        type="button"
                        disabled={Boolean(busyAction)}
                        onClick={() => runCancellationAction('cancellation-decline', declineJobCancellation, 'Prośba o anulowanie została odrzucona. Zlecenie pozostaje aktywne.')}
                      >
                        {busyAction === 'cancellation-decline' ? 'Odrzucanie…' : 'Odrzuć prośbę'}
                      </button>
                    </>
                  )}
                </div>
              </>
            ) : (
              <form className="job-details-cancellation__form" onSubmit={handleRequestCancellation}>
                <p>Anulowanie po przyjęciu zlecenia wymaga zgody drugiej strony. Do czasu akceptacji realizacja pozostaje aktywna.</p>
                <label htmlFor="cancellation-reason">Powód</label>
                <textarea
                  id="cancellation-reason"
                  maxLength={1000}
                  rows={4}
                  value={cancellationReason}
                  onChange={(event) => setCancellationReason(event.target.value)}
                  placeholder="Krótko wyjaśnij, dlaczego prosisz o anulowanie."
                  disabled={Boolean(busyAction)}
                />
                <button className="button button--secondary" type="submit" disabled={Boolean(busyAction) || !cancellationReason.trim()}>
                  {busyAction === 'cancellation-request' ? 'Wysyłanie…' : 'Poproś o anulowanie'}
                </button>
              </form>
            )}
          </section>
        )}

        <section className="job-details-panel job-details-panel--timeline">
          <span className="eyebrow">Historia</span>
          <h2>Przebieg zlecenia</h2>
          <div className="job-details-timeline">
            <TimelineItem label="Opublikowano" value={job.createdAt} />
            {job.takenAt && <TimelineItem label="Przyjęto do realizacji" value={job.takenAt} />}
            {job.completionRequestedAt && <TimelineItem label="Zgłoszono wykonanie" value={job.completionRequestedAt} />}
            {job.completedAt && <TimelineItem label="Potwierdzono zakończenie" value={job.completedAt} />}
            {job.cancelledAt && <TimelineItem label="Anulowano" value={job.cancelledAt} />}
            {!job.completedAt && !job.cancelledAt && job.updatedAt && <TimelineItem label={`Aktualny status: ${STATUS_LABELS[job.status] || job.status}`} value={job.updatedAt} />}
          </div>
        </section>
      </div>

      {reviewOpen && <ReviewDialog job={job} onClose={() => setReviewOpen(false)} onSubmitted={handleReviewSubmitted} />}
    </main>
  )
}

function TimelineItem({ label, value }) {
  return (
    <div className="job-details-timeline__item">
      <span className="job-details-timeline__dot" aria-hidden="true" />
      <div>
        <strong>{label}</strong>
        <span>{dateFormatter.format(new Date(value))}</span>
      </div>
    </div>
  )
}

function formatDistance(meters) {
  if (!meters) return '—'
  return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(meters < 10_000 ? 1 : 0)} km`
}

function formatDuration(seconds) {
  if (!seconds) return '—'
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  return remainder ? `${hours} godz. ${remainder} min` : `${hours} godz.`
}

export default JobDetailsPage
