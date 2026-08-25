import { Link } from 'react-router-dom'

const priceFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

const dateFormatter = new Intl.DateTimeFormat('pl-PL', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function JobCard({ job }) {
  return (
    <article className="job-card">
      <div className="job-card__header">
        <div>
          <span className="job-card__eyebrow">{routeLabel(job)}</span>
          <h3>{job.title}</h3>
        </div>
        <strong className="job-card__price">{priceFormatter.format(Number(job.price))}</strong>
      </div>

      {(job.routeDistanceMeters || job.routeDurationSeconds) && (
        <div className="job-card__route-meta">
          {job.routeDistanceMeters && <span>{formatDistance(job.routeDistanceMeters)}</span>}
          {job.routeDurationSeconds && <span>około {formatDuration(job.routeDurationSeconds)}</span>}
        </div>
      )}

      <p className="job-card__description">{job.description}</p>

      <footer className="job-card__footer">
        <span>{job.createdAt ? dateFormatter.format(new Date(job.createdAt)) : 'Nowe zlecenie'}</span>
        <Link to={`/users/${job.createdById}`}>Profil zlecającego</Link>
        <span className="job-card__status">Otwarte</span>
      </footer>
    </article>
  )
}

function routeLabel(job) {
  if (job.locationLabel && job.destinationLabel) return `${job.locationLabel} → ${job.destinationLabel}`
  return job.locationLabel || 'Lokalizacja do ustalenia'
}

function formatDistance(meters) {
  return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(meters < 10_000 ? 1 : 0)} km`
}

function formatDuration(seconds) {
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  return remainder ? `${hours} godz. ${remainder} min` : `${hours} godz.`
}

export default JobCard
