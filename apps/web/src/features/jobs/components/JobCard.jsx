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
          <span className="job-card__eyebrow">{job.locationLabel || 'Lokalizacja do ustalenia'}</span>
          <h3>{job.title}</h3>
        </div>
        <strong className="job-card__price">{priceFormatter.format(Number(job.price))}</strong>
      </div>

      <p className="job-card__description">{job.description}</p>

      <footer className="job-card__footer">
        <span>{job.createdAt ? dateFormatter.format(new Date(job.createdAt)) : 'Nowe zlecenie'}</span>
        <Link to={`/users/${job.createdById}`}>Profil zlecającego</Link>
        <span className="job-card__status">Otwarte</span>
      </footer>
    </article>
  )
}

export default JobCard
