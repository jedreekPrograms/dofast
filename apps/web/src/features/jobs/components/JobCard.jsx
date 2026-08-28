import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import ReportJobDialog from '../../jobReports/components/ReportJobDialog.jsx'
import { acceptJob, removeSavedJob, saveJob } from '../api/jobsApi.js'

const priceFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

const dateFormatter = new Intl.DateTimeFormat('pl-PL', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function JobCard({ job, showSaveAction = true, initialSaved = false, onSavedChange }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [accepting, setAccepting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(initialSaved)
  const [reportOpen, setReportOpen] = useState(false)
  const [reported, setReported] = useState(false)
  const [error, setError] = useState('')
  const proposalMode = job.assignmentMode === 'PROPOSALS'
  const canAccept = user && job.createdById !== user.id && job.status === 'OPEN' && !proposalMode
  const canPropose = user && job.createdById !== user.id && job.status === 'OPEN' && proposalMode
  const canSave = showSaveAction && user && job.createdById !== user.id && job.status === 'OPEN'
  const canReport = user && job.createdById !== user.id

  useEffect(() => {
    setSaved(initialSaved)
    setReported(false)
  }, [initialSaved, job.id])

  async function handleAccept() {
    setAccepting(true)
    setError('')
    try {
      const updated = await acceptJob(job.id)
      navigate(`/jobs/${updated.id}/route`)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się przyjąć zlecenia.')
    } finally {
      setAccepting(false)
    }
  }

  async function handleSaveToggle() {
    setSaving(true)
    setError('')
    try {
      if (saved) {
        await removeSavedJob(job.id)
        setSaved(false)
        onSavedChange?.(job.id, false)
      } else {
        await saveJob(job.id)
        setSaved(true)
        onSavedChange?.(job.id, true)
      }
    } catch (requestError) {
      setError(requestError.message || (saved
        ? 'Nie udało się usunąć zlecenia z zapisanych.'
        : 'Nie udało się zapisać zlecenia.'))
    } finally {
      setSaving(false)
    }
  }

  function handleReportSubmitted() {
    setReported(true)
    setReportOpen(false)
    setError('')
  }

  const locationSummary = job.fulfillmentMode === 'ON_SITE'
    ? (job.locationLabel || 'Lokalizacja do ustalenia')
    : `${job.locationLabel || 'Lokalizacja do ustalenia'} → ${job.destinationLabel || 'cel'}`

  return (
    <article className="job-card">
      <div className="job-card__header">
        <div>
          <span className="job-card__eyebrow">{locationSummary}</span>
          <h3>{job.title}</h3>
        </div>
        <strong className="job-card__price">{priceFormatter.format(Number(job.price))}</strong>
      </div>

      <p className="job-card__description">{job.description}</p>

      {proposalMode && (
        <div className="job-card__route">
          <strong>Zlecający wybiera wykonawcę</strong>
          <span>{job.priceNegotiationEnabled ? 'możesz zaproponować własną cenę' : 'wynagrodzenie jest stałe'}</span>
        </div>
      )}

      {(job.routeDistanceMeters || job.routeDurationSeconds) && (
        <div className="job-card__route">
          <strong>{formatDistance(job.routeDistanceMeters)}</strong>
          <span>około {formatDuration(job.routeDurationSeconds)}</span>
        </div>
      )}

      <div className="job-card__actions">
        {user && <Link className="button button--secondary" to={`/jobs/${job.id}`}>Szczegóły</Link>}
        {canSave && (
          <button
            className="button button--secondary"
            type="button"
            disabled={saving}
            aria-pressed={saved}
            onClick={handleSaveToggle}
          >
            {saving ? 'Zapisywanie…' : saved ? 'Usuń z zapisanych' : 'Zapisz'}
          </button>
        )}
        {canAccept && (
          <button className="button button--primary" type="button" disabled={accepting} onClick={handleAccept}>
            {accepting ? 'Przyjmowanie…' : 'Przyjmij zlecenie'}
          </button>
        )}
        {canPropose && <Link className="button button--primary" to={`/jobs/${job.id}`}>Wyślij propozycję</Link>}
        {canReport && (
          <button
            className="button button--secondary"
            type="button"
            disabled={reported}
            onClick={() => setReportOpen(true)}
          >
            {reported ? 'Zgłoszono' : 'Zgłoś ofertę'}
          </button>
        )}
        {!user && <Link className="button button--secondary" to="/login">{proposalMode ? 'Zaloguj się, aby złożyć propozycję' : 'Zaloguj się, aby przyjąć'}</Link>}
        {error && <span className="job-card__error">{error}</span>}
      </div>

      <footer className="job-card__footer">
        <span>{job.createdAt ? dateFormatter.format(new Date(job.createdAt)) : 'Nowe zlecenie'}</span>
        <Link to={`/users/${job.createdById}`}>Profil zlecającego</Link>
        <span className="job-card__status">{proposalMode ? 'Propozycje' : 'Otwarte'}</span>
      </footer>

      {reportOpen && (
        <ReportJobDialog
          job={job}
          onClose={() => setReportOpen(false)}
          onSubmitted={handleReportSubmitted}
        />
      )}
    </article>
  )
}

function formatDistance(meters) {
  if (!meters) return '—'
  return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(meters < 10_000 ? 1 : 0)} km`
}

function formatDuration(seconds) {
  if (!seconds) return '—'
  const minutes = Math.max(1, Math.round(seconds / 60))
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} godz. ${minutes % 60} min`
}

export default JobCard
