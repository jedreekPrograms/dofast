import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  deleteSavedSearch,
  getSavedSearches,
  getSavedSearchResults,
  updateSavedSearch,
} from '../api/jobsApi.js'
import './JobsPage.css'
import './SavedSearchesPage.css'

function buildSearchUrl(savedSearch) {
  const params = new URLSearchParams()
  if (savedSearch.query) params.set('query', savedSearch.query)
  if (savedSearch.categorySlug) params.set('category', savedSearch.categorySlug)
  if (savedSearch.minPrice != null) params.set('minPrice', String(savedSearch.minPrice))
  if (savedSearch.maxPrice != null) params.set('maxPrice', String(savedSearch.maxPrice))
  const query = params.toString()
  return query ? `/?${query}` : '/'
}

function formatPrice(value) {
  if (value == null) return null
  return `${Number(value).toLocaleString('pl-PL', { maximumFractionDigits: 2 })} zł`
}

function formatDistance(meters) {
  if (meters == null) return null
  return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(meters < 10_000 ? 1 : 0)} km`
}

function toUpdatePayload(savedSearch, alertsEnabled) {
  return {
    name: savedSearch.name,
    query: savedSearch.query,
    categorySlug: savedSearch.categorySlug,
    minPrice: savedSearch.minPrice,
    maxPrice: savedSearch.maxPrice,
    latitude: savedSearch.latitude,
    longitude: savedSearch.longitude,
    radiusKm: savedSearch.radiusKm,
    alertsEnabled,
  }
}

function SavedSearchesPage() {
  const [savedSearches, setSavedSearches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deletingId, setDeletingId] = useState(null)
  const [alertUpdatingId, setAlertUpdatingId] = useState(null)
  const [resultsLoadingId, setResultsLoadingId] = useState(null)
  const [resultsById, setResultsById] = useState({})
  const [resultsErrorById, setResultsErrorById] = useState({})

  const loadSavedSearches = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      setSavedSearches(await getSavedSearches({ signal }))
    } catch (requestError) {
      if (requestError.name !== 'AbortError') {
        setError(requestError.message || 'Nie udało się pobrać zapisanych wyszukiwań.')
      }
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    loadSavedSearches(controller.signal)
    return () => controller.abort()
  }, [loadSavedSearches])

  async function handleDelete(id) {
    setDeletingId(id)
    setError('')
    try {
      await deleteSavedSearch(id)
      setSavedSearches((current) => current.filter((savedSearch) => savedSearch.id !== id))
      setResultsById((current) => {
        const next = { ...current }
        delete next[id]
        return next
      })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się usunąć zapisanego wyszukiwania.')
    } finally {
      setDeletingId(null)
    }
  }

  async function handleAlertToggle(savedSearch) {
    setAlertUpdatingId(savedSearch.id)
    setError('')
    try {
      const updated = await updateSavedSearch(
        savedSearch.id,
        toUpdatePayload(savedSearch, !savedSearch.alertsEnabled),
      )
      setSavedSearches((current) => current.map((item) => (item.id === updated.id ? updated : item)))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zmienić ustawienia alertu.')
    } finally {
      setAlertUpdatingId(null)
    }
  }

  async function handleRadiusResults(savedSearch) {
    const id = savedSearch.id
    if (resultsById[id]) {
      setResultsById((current) => {
        const next = { ...current }
        delete next[id]
        return next
      })
      return
    }

    setResultsLoadingId(id)
    setResultsErrorById((current) => ({ ...current, [id]: '' }))
    try {
      const results = await getSavedSearchResults(id)
      setResultsById((current) => ({ ...current, [id]: results }))
    } catch (requestError) {
      setResultsErrorById((current) => ({
        ...current,
        [id]: requestError.message || 'Nie udało się pobrać wyników z prywatnego obszaru.',
      }))
    } finally {
      setResultsLoadingId(null)
    }
  }

  return (
    <main className="jobs-page saved-searches-page">
      <header className="jobs-hero">
        <span className="jobs-hero__badge">Twoje filtry</span>
        <h1>Zapisane wyszukiwania</h1>
        <p>
          Wracaj jednym kliknięciem do filtrów i włącz alert, aby dostać powiadomienie o nowym pasującym zleceniu.
        </p>
      </header>

      <section className="jobs-results" aria-live="polite">
        <div className="jobs-results__heading">
          <div>
            <h2>Presety discovery</h2>
            {!loading && !error && <p>{savedSearches.length} z maksymalnie 20 zapisanych wyszukiwań</p>}
          </div>
          <Link className="button button--secondary saved-searches-page__new" to="/">
            Utwórz z filtrów
          </Link>
        </div>

        {loading && <div className="jobs-state">Pobieranie zapisanych wyszukiwań…</div>}
        {!loading && error && <div className="jobs-state jobs-state--error">{error}</div>}
        {!loading && !error && savedSearches.length === 0 && (
          <div className="jobs-state">
            Nie masz jeszcze zapisanych wyszukiwań. Ustaw filtry na stronie zleceń i zapisz je jako preset.
          </div>
        )}

        {!loading && !error && savedSearches.length > 0 && (
          <div className="saved-searches-grid">
            {savedSearches.map((savedSearch) => {
              const minPrice = formatPrice(savedSearch.minPrice)
              const maxPrice = formatPrice(savedSearch.maxPrice)
              const radiusResults = resultsById[savedSearch.id]
              const radiusResultsError = resultsErrorById[savedSearch.id]
              return (
                <article className="saved-search-card" key={savedSearch.id}>
                  <div>
                    <span className="jobs-hero__badge">Wyszukiwanie</span>
                    <h3>{savedSearch.name}</h3>
                  </div>

                  <dl className="saved-search-card__filters">
                    {savedSearch.query && (
                      <div>
                        <dt>Fraza</dt>
                        <dd>{savedSearch.query}</dd>
                      </div>
                    )}
                    {savedSearch.categoryName && (
                      <div>
                        <dt>Kategoria</dt>
                        <dd>{savedSearch.categoryName}</dd>
                      </div>
                    )}
                    {(minPrice || maxPrice) && (
                      <div>
                        <dt>Cena</dt>
                        <dd>
                          {minPrice && maxPrice && `${minPrice} – ${maxPrice}`}
                          {minPrice && !maxPrice && `od ${minPrice}`}
                          {!minPrice && maxPrice && `do ${maxPrice}`}
                        </dd>
                      </div>
                    )}
                    {savedSearch.radiusKm != null && (
                      <div>
                        <dt>Obszar alertu</dt>
                        <dd>do {savedSearch.radiusKm} km od prywatnego punktu</dd>
                      </div>
                    )}
                    <div>
                      <dt>Alert o nowych zleceniach</dt>
                      <dd>{savedSearch.alertsEnabled ? 'Włączony' : 'Wyłączony'}</dd>
                    </div>
                  </dl>

                  <div className="saved-search-card__actions">
                    {savedSearch.radiusKm == null ? (
                      <Link className="button button--primary" to={buildSearchUrl(savedSearch)}>
                        Pokaż wyniki
                      </Link>
                    ) : (
                      <button
                        className="button button--primary"
                        type="button"
                        disabled={resultsLoadingId === savedSearch.id}
                        onClick={() => handleRadiusResults(savedSearch)}
                      >
                        {resultsLoadingId === savedSearch.id
                          ? 'Pobieranie…'
                          : radiusResults
                            ? 'Ukryj wyniki'
                            : 'Pokaż wyniki'}
                      </button>
                    )}
                    <button
                      className="button button--secondary"
                      type="button"
                      disabled={alertUpdatingId === savedSearch.id}
                      onClick={() => handleAlertToggle(savedSearch)}
                    >
                      {alertUpdatingId === savedSearch.id
                        ? 'Zapisywanie…'
                        : savedSearch.alertsEnabled
                          ? 'Wyłącz alert'
                          : 'Włącz alert'}
                    </button>
                    <button
                      className="button button--secondary"
                      type="button"
                      disabled={deletingId === savedSearch.id}
                      onClick={() => handleDelete(savedSearch.id)}
                    >
                      {deletingId === savedSearch.id ? 'Usuwanie…' : 'Usuń'}
                    </button>
                  </div>

                  {radiusResultsError && (
                    <div className="jobs-state jobs-state--error">{radiusResultsError}</div>
                  )}
                  {radiusResults && (
                    <div className="saved-search-card__private-results">
                      <p>{radiusResults.length} najbliższych pasujących zleceń</p>
                      {radiusResults.length === 0 && <div className="jobs-state">Brak aktualnych zleceń w tym obszarze.</div>}
                      {radiusResults.map((job) => (
                        <div className="saved-search-result" key={job.id}>
                          <div>
                            <strong>{job.title}</strong>
                            <span>{job.locationLabel || 'Lokalizacja do ustalenia'}</span>
                          </div>
                          <div>
                            <strong>{formatPrice(job.price)}</strong>
                            <span>{formatDistance(job.distanceMeters)}</span>
                          </div>
                          <Link className="button button--secondary" to={`/jobs/${job.id}`}>
                            Szczegóły
                          </Link>
                        </div>
                      ))}
                    </div>
                  )}
                </article>
              )
            })}
          </div>
        )}
      </section>
    </main>
  )
}

export default SavedSearchesPage
