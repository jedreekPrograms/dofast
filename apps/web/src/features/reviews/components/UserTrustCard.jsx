import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getPublicProfile } from '../api/reviewsApi.js'
import './UserTrustCard.css'

const profileCache = new Map()
const profileRequests = new Map()

function fetchProfile(userId) {
  if (profileCache.has(userId)) {
    return Promise.resolve(profileCache.get(userId))
  }
  if (profileRequests.has(userId)) {
    return profileRequests.get(userId)
  }

  const request = getPublicProfile(userId)
    .then((profile) => {
      profileCache.set(userId, profile)
      return profile
    })
    .finally(() => {
      profileRequests.delete(userId)
    })

  profileRequests.set(userId, request)
  return request
}

function UserTrustCard({ userId, roleLabel = 'Użytkownik', compact = false }) {
  const [loaded, setLoaded] = useState(null)
  const [failedUserId, setFailedUserId] = useState(null)

  const cachedProfile = userId ? profileCache.get(userId) || null : null
  const loadedProfile = loaded?.userId === userId ? loaded.profile : null
  const profile = cachedProfile || loadedProfile
  const failed = Boolean(userId && failedUserId === userId && !profile)
  const loading = Boolean(userId && !profile && !failed)

  useEffect(() => {
    if (!userId || profileCache.has(userId)) {
      return undefined
    }

    let active = true
    fetchProfile(userId)
      .then((response) => {
        if (!active) return
        setLoaded({ userId, profile: response })
        setFailedUserId(null)
      })
      .catch(() => {
        if (active) setFailedUserId(userId)
      })

    return () => { active = false }
  }, [userId])

  if (!userId) {
    return (
      <article className={`user-trust-card ${compact ? 'user-trust-card--compact' : ''} user-trust-card--empty`}>
        <span className="user-trust-card__role">{roleLabel}</span>
        <strong>Jeszcze nie wybrano</strong>
        <span>Profil pojawi się po przypisaniu użytkownika do zlecenia.</span>
      </article>
    )
  }

  if (loading) {
    return (
      <article className={`user-trust-card ${compact ? 'user-trust-card--compact' : ''}`} aria-busy="true">
        <span className="user-trust-card__role">{roleLabel}</span>
        <strong>Pobieranie profilu zaufania…</strong>
      </article>
    )
  }

  if (failed || !profile) {
    return (
      <article className={`user-trust-card ${compact ? 'user-trust-card--compact' : ''}`}>
        <span className="user-trust-card__role">{roleLabel}</span>
        <strong>Użytkownik #{userId}</strong>
        <span className="user-trust-card__muted">Nie udało się pobrać podsumowania reputacji.</span>
        <Link className="user-trust-card__link" to={`/users/${userId}`}>Otwórz pełny profil</Link>
      </article>
    )
  }

  const specializations = profile.serviceCategories || []
  const visibleSpecializations = specializations.slice(0, compact ? 3 : 5)
  const hiddenSpecializations = specializations.length - visibleSpecializations.length

  return (
    <article className={`user-trust-card ${compact ? 'user-trust-card--compact' : ''}`}>
      <div className="user-trust-card__header">
        <div className="user-trust-card__avatar" aria-hidden="true">
          {profile.username?.slice(0, 1).toUpperCase() || '?'}
        </div>
        <div className="user-trust-card__identity">
          <span className="user-trust-card__role">{roleLabel}</span>
          <div className="user-trust-card__name-row">
            <strong>{profile.username}</strong>
            {profile.identityVerified && <span className="user-trust-card__verified" title="Tożsamość zweryfikowana">✓ Zweryfikowany</span>}
          </div>
          {profile.publicLocation && <span className="user-trust-card__location">{profile.publicLocation}</span>}
        </div>
      </div>

      <div className="user-trust-card__metrics" aria-label="Podsumowanie reputacji">
        <div>
          <span>Ocena</span>
          <strong>{profile.averageRating == null ? '—' : `${Number(profile.averageRating).toFixed(1)} / 5`}</strong>
          <small>{profile.reviewsCount} opinii</small>
        </div>
        <div>
          <span>Zakończone</span>
          <strong>{profile.completedJobsTotal}</strong>
          <small>zleceń</small>
        </div>
        {!compact && (
          <div>
            <span>W doFast od</span>
            <strong>{formatMemberSince(profile.memberSince)}</strong>
          </div>
        )}
      </div>

      {visibleSpecializations.length > 0 && (
        <div className="user-trust-card__specializations" aria-label="Specjalizacje">
          {visibleSpecializations.map((category) => (
            <span key={category.id} title={category.parentCategoryName}>{category.name}</span>
          ))}
          {hiddenSpecializations > 0 && <span>+{hiddenSpecializations}</span>}
        </div>
      )}

      {!compact && profile.bio && <p className="user-trust-card__bio">{profile.bio}</p>}

      <Link className="user-trust-card__link" to={`/users/${userId}`}>Zobacz pełny profil i opinie →</Link>
    </article>
  )
}

function formatMemberSince(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('pl-PL', { year: 'numeric', month: 'short' })
}

export default UserTrustCard
