import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { createReview, getReviewEligibility } from '../api/reviewsApi.js'
import './ReviewDialog.css'

function ReviewDialog({ job, onClose, onSubmitted }) {
  const [eligibility, setEligibility] = useState(null)
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    getReviewEligibility(job.id)
      .then((response) => {
        if (active) setEligibility(response)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się sprawdzić możliwości wystawienia opinii.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [job.id])

  async function submit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const review = await createReview({ jobId: job.id, rating, comment })
      onSubmitted(review)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zapisać opinii.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="review-dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="review-dialog" role="dialog" aria-modal="true" aria-labelledby="review-dialog-title">
        <button className="review-dialog__close" type="button" aria-label="Zamknij" onClick={onClose}>×</button>
        <span className="eyebrow">Zaufanie po zleceniu</span>
        <h2 id="review-dialog-title">Oceń współpracę</h2>
        <p className="review-dialog__job">{job.title} · #{job.id}</p>

        {loading && <div className="page-state">Sprawdzanie możliwości wystawienia opinii…</div>}
        {error && <div className="form-message form-message--error">{error}</div>}

        {!loading && eligibility?.alreadyReviewed && (
          <div className="review-dialog__done">
            <strong>Opinia została już wystawiona.</strong>
            <p>Każda strona może ocenić drugą stronę tylko raz za konkretne zlecenie.</p>
            {eligibility.counterpartId && <Link to={`/users/${eligibility.counterpartId}`}>Zobacz profil użytkownika</Link>}
          </div>
        )}

        {!loading && eligibility && !eligibility.eligible && !eligibility.alreadyReviewed && (
          <div className="form-message">To zlecenie nie kwalifikuje się obecnie do wystawienia opinii.</div>
        )}

        {!loading && eligibility?.eligible && (
          <form className="review-form" onSubmit={submit}>
            <div className="review-form__counterpart">
              <span>Oceniasz</span>
              <Link to={`/users/${eligibility.counterpartId}`}>{eligibility.counterpartNickname}</Link>
            </div>

            <fieldset className="review-stars">
              <legend>Ocena</legend>
              <div>
                {[1, 2, 3, 4, 5].map((value) => (
                  <button
                    key={value}
                    type="button"
                    className={value <= rating ? 'is-active' : ''}
                    aria-label={`${value} z 5`}
                    onClick={() => setRating(value)}
                  >★</button>
                ))}
              </div>
              <strong>{rating}/5</strong>
            </fieldset>

            <label>
              Komentarz <span>opcjonalnie</span>
              <textarea
                rows={5}
                maxLength={2000}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                placeholder="Napisz krótko, jak przebiegła współpraca."
              />
            </label>

            <div className="review-form__actions">
              <button className="button button--secondary" type="button" onClick={onClose}>Anuluj</button>
              <button className="button button--primary" type="submit" disabled={submitting}>
                {submitting ? 'Zapisywanie…' : 'Wystaw opinię'}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  )
}

export default ReviewDialog
