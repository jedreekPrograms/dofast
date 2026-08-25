import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import ReviewDialog from '../../reviews/components/ReviewDialog.jsx'
import { cancelJob, confirmJobCompletion, getMyJobs, requestJobCompletion } from '../api/jobsApi.js'
import './MyJobsPage.css'

const STATUS_LABELS = {
  OPEN: 'Otwarte', IN_PROGRESS: 'W realizacji', COMPLETION_REQUESTED: 'Do potwierdzenia',
  DISPUTED: 'W sporze', DONE: 'Zakończone', CANCELLED: 'Anulowane',
}
const WORKER_ROUTE_STATUSES = new Set(['IN_PROGRESS', 'COMPLETION_REQUESTED', 'DISPUTED'])

function MyJobsPage() {
  const { user } = useAuth()
  const [jobs, setJobs] = useState([])
  const [tab, setTab] = useState('created')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [actionId, setActionId] = useState(null)
  const [reviewJob, setReviewJob] = useState(null)
  const [reviewedJobIds, setReviewedJobIds] = useState(() => new Set())

  const loadJobs = useCallback(async () => {
    setLoading(true); setError('')
    try { setJobs(await getMyJobs()) }
    catch (requestError) { setError(requestError.message || 'Nie udało się pobrać Twoich zleceń.') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { loadJobs() }, [loadJobs])

  const visibleJobs = useMemo(() => jobs.filter((job) => (
    tab === 'created' ? job.createdById === user.id : job.takenById === user.id
  )), [jobs, tab, user.id])

  async function runAction(jobId, action) {
    setActionId(jobId); setError(''); setSuccess('')
    try {
      const updated = await action(jobId)
      setJobs((current) => current.map((job) => job.id === updated.id ? updated : job))
    } catch (requestError) { setError(requestError.message || 'Nie udało się wykonać operacji.') }
    finally { setActionId(null) }
  }

  function handleReviewSubmitted(review) {
    setReviewedJobIds((current) => new Set(current).add(review.jobId))
    setReviewJob(null)
    setSuccess('Opinia została zapisana i jest już widoczna na profilu użytkownika.')
  }

  return (
    <main className="my-jobs-page">
      <header className="page-heading page-heading--row"><div><span className="eyebrow">Panel zleceń</span><h1>Moje zlecenia</h1><p>Kontroluj zadania, które wystawiłeś, oraz te, których wykonanie przyjąłeś.</p></div></header>
      <div className="segmented-control" role="tablist" aria-label="Typ zleceń">
        <button className={tab === 'created' ? 'is-active' : ''} type="button" onClick={() => setTab('created')}>Zlecone przeze mnie</button>
        <button className={tab === 'taken' ? 'is-active' : ''} type="button" onClick={() => setTab('taken')}>Wzięte przeze mnie</button>
      </div>
      {error && <div className="form-message form-message--error">{error}</div>}
      {success && <div className="form-message form-message--success">{success}</div>}
      {loading && <div className="page-state">Pobieranie zleceń…</div>}
      {!loading && visibleJobs.length === 0 && <div className="page-state">W tej sekcji nie ma jeszcze żadnych zleceń.</div>}

      {!loading && visibleJobs.length > 0 && <div className="my-jobs-list">
        {visibleJobs.map((job) => {
          const counterpartId = tab === 'created' ? job.takenById : job.createdById
          const canOpenRoute = tab === 'created' || WORKER_ROUTE_STATUSES.has(job.status)
          return <article className="my-job" key={job.id}>
            <div className="my-job__body">
              <div className="my-job__meta">
                <span className={`status-pill status-pill--${job.status.toLowerCase()}`}>{STATUS_LABELS[job.status] || job.status}</span>
                <span>{routeLabel(job)}</span>
                {job.routeDistanceMeters && <span>{formatDistance(job.routeDistanceMeters)}</span>}
                {job.routeDurationSeconds && <span>~ {formatDuration(job.routeDurationSeconds)}</span>}
              </div>
              <h2>{job.title}</h2><p>{job.description}</p>
              <div className="my-job__footer"><strong>{Number(job.price).toLocaleString('pl-PL', { style: 'currency', currency: 'PLN' })}</strong><span>#{job.id}</span>{counterpartId && <Link to={`/users/${counterpartId}`}>Profil drugiej strony</Link>}</div>
            </div>
            <div className="my-job__actions">
              {canOpenRoute && job.destinationLabel && <Link className="button button--secondary" to={`/jobs/${job.id}/route`}>Trasa A → B</Link>}
              {job.takenById && <Link className="button button--secondary" to={`/chat?jobId=${job.id}`}>Czat</Link>}
              {job.status === 'DONE' && <button className="button button--secondary" type="button" disabled={reviewedJobIds.has(job.id)} onClick={() => setReviewJob(job)}>{reviewedJobIds.has(job.id) ? 'Oceniono' : 'Oceń współpracę'}</button>}
              {tab === 'created' && job.status === 'OPEN' && <button className="button button--danger" type="button" disabled={actionId === job.id} onClick={() => runAction(job.id, cancelJob)}>Anuluj</button>}
              {tab === 'created' && job.status === 'COMPLETION_REQUESTED' && <button className="button button--primary" type="button" disabled={actionId === job.id} onClick={() => runAction(job.id, confirmJobCompletion)}>Potwierdź wykonanie</button>}
              {tab === 'taken' && job.status === 'IN_PROGRESS' && <button className="button button--primary" type="button" disabled={actionId === job.id} onClick={() => runAction(job.id, requestJobCompletion)}>Zgłoś wykonanie</button>}
              {['IN_PROGRESS', 'COMPLETION_REQUESTED'].includes(job.status) && <Link className="button button--secondary" to={`/disputes?jobId=${job.id}`}>Otwórz spór</Link>}
              {job.status === 'DISPUTED' && <Link className="button button--secondary" to="/disputes">Zobacz spór</Link>}
            </div>
          </article>
        })}
      </div>}
      {reviewJob && <ReviewDialog job={reviewJob} onClose={() => setReviewJob(null)} onSubmitted={handleReviewSubmitted} />}
    </main>
  )
}

function routeLabel(job) { return job.destinationLabel ? `${job.locationLabel} → ${job.destinationLabel}` : job.locationLabel }
function formatDistance(meters) { return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(meters < 10_000 ? 1 : 0)} km` }
function formatDuration(seconds) { const minutes = Math.max(1, Math.round(seconds / 60)); if (minutes < 60) return `${minutes} min`; const hours = Math.floor(minutes / 60); const remainder = minutes % 60; return remainder ? `${hours} godz. ${remainder} min` : `${hours} godz.` }

export default MyJobsPage
