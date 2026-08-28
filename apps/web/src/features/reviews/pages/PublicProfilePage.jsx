import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getPublicProfile, getReceivedReviews } from '../api/reviewsApi.js'
import './PublicProfilePage.css'

function formatDate(value) {
  return value ? new Date(value).toLocaleString('pl-PL') : ''
}

function formatMemberSince(value) {
  return value ? new Date(value).toLocaleDateString('pl-PL', { year: 'numeric', month: 'long' }) : '—'
}

function PublicProfilePage() {
  const { userId } = useParams()
  const [profile, setProfile] = useState(null)
  const [reviews, setReviews] = useState(null)
  const [loading, setLoading] = useState(true)
  const [reviewsLoading, setReviewsLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([
      getPublicProfile(userId),
      getReceivedReviews(userId, { page: 0, size: 10 }),
    ])
      .then(([profileResponse, reviewsResponse]) => {
        if (!active) return
        setProfile(profileResponse)
        setReviews(reviewsResponse)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać profilu.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [userId])

  async function changeReviewPage(nextPage) {
    setReviewsLoading(true)
    setError('')
    try {
      setReviews(await getReceivedReviews(userId, { page: nextPage, size: 10 }))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać kolejnych opinii.')
    } finally {
      setReviewsLoading(false)
    }
  }

  if (loading) {
    return <main className="public-profile-page"><div className="page-state">Pobieranie profilu…</div></main>
  }

  if (!profile) {
    return <main className="public-profile-page"><div className="form-message form-message--error">{error || 'Profil nie istnieje.'}</div></main>
  }

  return (
    <main className="public-profile-page">
      <section className="trust-profile panel">
        <div className="trust-profile__identity">
          <div className="trust-profile__avatar" aria-hidden="true">{profile.username?.slice(0, 1).toUpperCase()}</div>
          <div>
            <span className="eyebrow">Profil użytkownika</span>
            <div className="trust-profile__name-row">
              <h1>{profile.username}</h1>
              {profile.identityVerified && <span className="identity-badge" title="Tożsamość zweryfikowana">✓ Zweryfikowana tożsamość</span>}
            </div>
            <p>{profile.publicLocation || 'Publiczna historia współpracy w doFast.'}</p>
          </div>
        </div>

        <div className="trust-profile__about">
          <div>
            <span>W doFast od</span>
            <strong>{formatMemberSince(profile.memberSince)}</strong>
          </div>
          <div className="trust-profile__bio">
            <span>O użytkowniku</span>
            <p>{profile.bio || 'Ten użytkownik nie dodał jeszcze publicznego opisu.'}</p>
          </div>
        </div>

        <div className="trust-profile__stats">
          <div>
            <span>Ocena</span>
            <strong>{profile.averageRating == null ? '—' : `${Number(profile.averageRating).toFixed(1)} / 5`}</strong>
            <small>{profile.reviewsCount} opinii</small>
          </div>
          <div>
            <span>Zakończone zlecenia</span>
            <strong>{profile.completedJobsTotal}</strong>
            <small>łącznie po obu stronach</small>
          </div>
          <div>
            <span>Jako zlecający</span>
            <strong>{profile.completedJobsAsRequester}</strong>
            <small>zakończonych</small>
          </div>
          <div>
            <span>Jako wykonawca</span>
            <strong>{profile.completedJobsAsWorker}</strong>
            <small>zakończonych</small>
          </div>
        </div>
      </section>

      {error && <div className="form-message form-message--error">{error}</div>}

      <section className="trust-reviews">
        <div className="page-heading page-heading--row">
          <div>
            <span className="eyebrow">Reputacja</span>
            <h2>Otrzymane opinie</h2>
          </div>
          {reviews && <strong>{reviews.totalElements}</strong>}
        </div>

        {reviewsLoading && <div className="page-state">Pobieranie opinii…</div>}
        {!reviewsLoading && reviews?.content.length === 0 && (
          <div className="page-state">Ten użytkownik nie otrzymał jeszcze żadnej opinii.</div>
        )}

        {!reviewsLoading && reviews?.content.length > 0 && (
          <div className="trust-review-list">
            {reviews.content.map((review) => (
              <article className="trust-review panel" key={review.id}>
                <div className="trust-review__heading">
                  <div>
                    <div className="trust-review__stars" aria-label={`Ocena ${review.rating} z 5`}>
                      {'★'.repeat(review.rating)}<span>{'★'.repeat(5 - review.rating)}</span>
                    </div>
                    <strong>{review.rating}/5</strong>
                  </div>
                  <time>{formatDate(review.createdAt)}</time>
                </div>
                {review.comment && <p>{review.comment}</p>}
                <footer>
                  <span>Za zlecenie „{review.jobTitle}”</span>
                  <Link to={`/users/${review.reviewerId}`}>{review.reviewerNickname}</Link>
                </footer>
              </article>
            ))}
          </div>
        )}

        {reviews && reviews.totalPages > 1 && (
          <div className="pagination">
            <button type="button" disabled={reviews.first || reviewsLoading} onClick={() => changeReviewPage(reviews.page - 1)}>Poprzednia</button>
            <span>{reviews.page + 1} / {reviews.totalPages}</span>
            <button type="button" disabled={reviews.last || reviewsLoading} onClick={() => changeReviewPage(reviews.page + 1)}>Następna</button>
          </div>
        )}
      </section>
    </main>
  )
}

export default PublicProfilePage
