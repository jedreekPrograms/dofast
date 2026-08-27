import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteSavedSearch, getSavedSearches, updateSavedSearch } from '../api/jobsApi.js'
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

function toUpdatePayload(savedSearch, alertsEnabled) {
  return {
    name: savedSearch.name,
    query: savedSearch.query,
    categorySlug: savedSearch.categorySlug,
    minPrice: savedSearch.minPrice,
    maxPrice: savedSearch.maxPrice,
    alertsEnabled,
  }
}

function SavedSearchesPage() {
  const [savedSearches, setSavedSearches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deletingId, setDeletingId] = useState(null)
  const [alertUpdatingId, setAlertUpdatingId] = useState(null)

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
                    <div>
                      <dt>Alert o nowych zleceniach</dt>
                      <dd>{savedSearch.alertsEnabled ? 'Włączony' : 'Wyłączony'}</dd>
                    </div>
                  </dl>

                  <div className="saved-search-card__actions">
                    <Link className="button button--primary" to={buildSearchUrl(savedSearch)}>
                      Pokaż wyniki
                    </Link>
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
