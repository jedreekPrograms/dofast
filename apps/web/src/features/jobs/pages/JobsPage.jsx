import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import JobCard from '../components/JobCard.jsx'
import LocationMapPicker from '../components/LocationMapPicker.jsx'
import {
  createSavedSearch,
  getJobCategories,
  getJobs,
  getRecommendedJobs,
  getSavedJobStatuses,
} from '../api/jobsApi.js'
import './JobsPage.css'

const DEFAULT_FILTERS = {
  query: '',
  category: '',
  minPrice: '',
  maxPrice: '',
  page: 0,
  size: 12,
}

function filtersFromSearchParams(searchParams) {
  return {
    ...DEFAULT_FILTERS,
    query: searchParams.get('query') ?? '',
    category: searchParams.get('category') ?? '',
    minPrice: searchParams.get('minPrice') ?? '',
    maxPrice: searchParams.get('maxPrice') ?? '',
  }
}

function filtersToSearchParams(filters) {
  const params = new URLSearchParams()
  if (filters.query?.trim()) params.set('query', filters.query.trim())
  if (filters.category) params.set('category', filters.category)
  if (filters.minPrice !== '') params.set('minPrice', filters.minPrice)
  if (filters.maxPrice !== '') params.set('maxPrice', filters.maxPrice)
  return params
}

function JobsPage() {
  const { user } = useAuth()
  const userId = user?.id ?? null
  const [searchParams, setSearchParams] = useSearchParams()
  const initialFilters = filtersFromSearchParams(searchParams)
  const [draftFilters, setDraftFilters] = useState(initialFilters)
  const [filters, setFilters] = useState(initialFilters)
  const [categories, setCategories] = useState([])
  const [result, setResult] = useState(null)
  const [recommendations, setRecommendations] = useState(null)
  const [savedJobIds, setSavedJobIds] = useState(() => new Set())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [recommendationsLoading, setRecommendationsLoading] = useState(false)
  const [recommendationsError, setRecommendationsError] = useState('')
  const [savedSearchName, setSavedSearchName] = useState('')
  const [savingSearch, setSavingSearch] = useState(false)
  const [savedSearchMessage, setSavedSearchMessage] = useState('')
  const [savedSearchError, setSavedSearchError] = useState('')
  const [limitSavedSearchByLocation, setLimitSavedSearchByLocation] = useState(false)
  const [savedSearchLocation, setSavedSearchLocation] = useState(null)
  const [savedSearchRadiusKm, setSavedSearchRadiusKm] = useState('10')

  const applySavedStatuses = useCallback((jobIds, savedIds) => {
    const savedSet = new Set(savedIds)
    setSavedJobIds((current) => {
      const next = new Set(current)
      jobIds.forEach((jobId) => {
        if (savedSet.has(jobId)) next.add(jobId)
        else next.delete(jobId)
      })
      return next
    })
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    getJobCategories({ signal: controller.signal })
      .then(setCategories)
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setCategories([])
      })

    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    async function loadJobs() {
      setLoading(true)
      setError('')

      try {
        const data = await getJobs(filters, { signal: controller.signal })

        if (userId && data.content?.length) {
          try {
            const jobIds = data.content.map((job) => job.id)
            const statuses = await getSavedJobStatuses(jobIds, { signal: controller.signal })
            applySavedStatuses(jobIds, statuses.savedJobIds ?? [])
          } catch (requestError) {
            if (requestError.name === 'AbortError') throw requestError
          }
        }

        setResult(data)
      } catch (requestError) {
        if (requestError.name !== 'AbortError') {
          setError('Nie udało się pobrać zleceń. Spróbuj ponownie.')
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      }
    }

    loadJobs()
    return () => controller.abort()
  }, [applySavedStatuses, filters, userId])

  useEffect(() => {
    if (!userId) return undefined

    const controller = new AbortController()

    async function loadRecommendations() {
      setRecommendationsLoading(true)
      setRecommendationsError('')

      try {
        const data = await getRecommendedJobs(0, 6, { signal: controller.signal })
        const recommendedJobs = data.jobs?.content ?? []

        if (recommendedJobs.length) {
          try {
            const jobIds = recommendedJobs.map((job) => job.id)
            const statuses = await getSavedJobStatuses(jobIds, { signal: controller.signal })
            applySavedStatuses(jobIds, statuses.savedJobIds ?? [])
          } catch (requestError) {
            if (requestError.name === 'AbortError') throw requestError
          }
        }

        setRecommendations(data)
      } catch (requestError) {
        if (requestError.name !== 'AbortError') {
          setRecommendationsError('Nie udało się pobrać rekomendowanych zleceń.')
        }
      } finally {
        if (!controller.signal.aborted) setRecommendationsLoading(false)
      }
    }

    loadRecommendations()
    return () => controller.abort()
  }, [applySavedStatuses, userId])

  function updateDraft(event) {
    const { name, value } = event.target
    setDraftFilters((current) => ({ ...current, [name]: value }))
  }

  function applyFilters(event) {
    event.preventDefault()
    const nextFilters = { ...draftFilters, page: 0 }
    setFilters(nextFilters)
    setSearchParams(filtersToSearchParams(nextFilters))
    setSavedSearchMessage('')
    setSavedSearchError('')
  }

  function clearFilters() {
    setDraftFilters(DEFAULT_FILTERS)
    setFilters(DEFAULT_FILTERS)
    setSearchParams({})
    setSavedSearchMessage('')
    setSavedSearchError('')
  }

  function changePage(nextPage) {
    setFilters((current) => ({ ...current, page: nextPage }))
  }

  function updateSavedState(jobId, saved) {
    setSavedJobIds((current) => {
      const next = new Set(current)
      if (saved) next.add(jobId)
      else next.delete(jobId)
      return next
    })
  }

  async function saveCurrentSearch(event) {
    event.preventDefault()
    setSavingSearch(true)
    setSavedSearchMessage('')
    setSavedSearchError('')

    const radiusKm = Number(savedSearchRadiusKm)
    if (limitSavedSearchByLocation && !hasValidSavedSearchLocation(savedSearchLocation, radiusKm)) {
      setSavingSearch(false)
      setSavedSearchError('Wybierz punkt na mapie i promień od 1 do 100 km.')
      return
    }

    try {
      await createSavedSearch({
        name: savedSearchName,
        query: filters.query?.trim() || null,
        categorySlug: filters.category || null,
        minPrice: filters.minPrice === '' ? null : Number(filters.minPrice),
        maxPrice: filters.maxPrice === '' ? null : Number(filters.maxPrice),
        latitude: limitSavedSearchByLocation ? Number(savedSearchLocation.latitude) : null,
        longitude: limitSavedSearchByLocation ? Number(savedSearchLocation.longitude) : null,
        radiusKm: limitSavedSearchByLocation ? radiusKm : null,
      })
      setSavedSearchName('')
      setSavedSearchMessage('Wyszukiwanie zapisane. Znajdziesz je w „Wyszukiwaniach”.')
    } catch (requestError) {
      setSavedSearchError(requestError.message || 'Nie udało się zapisać wyszukiwania.')
    } finally {
      setSavingSearch(false)
    }
  }

  const jobs = result?.content ?? []
  const recommendedJobs = recommendations?.jobs?.content ?? []
  const hasPublicFilters = Boolean(
    filters.query?.trim()
      || filters.category
      || filters.minPrice !== ''
      || filters.maxPrice !== '',
  )
  const savedSearchRadius = Number(savedSearchRadiusKm)
  const hasValidLocationCriterion = limitSavedSearchByLocation
    && hasValidSavedSearchLocation(savedSearchLocation, savedSearchRadius)
  const canSaveSearch = hasPublicFilters || hasValidLocationCriterion

  return (
    <main className="jobs-page">
      <header className="jobs-hero">
        <span className="jobs-hero__badge">Zlecenia lokalne</span>
        <h1>Znajdź zlecenie blisko siebie</h1>
        <p>
          Przeglądaj aktualne zadania i filtruj je po kategorii, cenie, nazwie, opisie lub obszarze.
        </p>
      </header>

      {user && (
        <section className="jobs-recommended" aria-labelledby="jobs-recommended-title">
          <div className="jobs-results__heading">
            <div>
              <span className="jobs-hero__badge">Dla Ciebie</span>
              <h2 id="jobs-recommended-title">Zgodne z Twoimi specjalizacjami</h2>
              {recommendations?.specializationCount > 0 && (
                <p>
                  Dopasowanie na podstawie {recommendations.specializationCount} aktywnych specjalizacji.
                  {recommendations.jobs && ` ${recommendations.jobs.totalElements} otwartych ofert.`}
                </p>
              )}
            </div>
            <Link className="button button--secondary jobs-recommended__profile-link" to="/profile">
              Edytuj specjalizacje
            </Link>
          </div>

          {recommendationsLoading && <div className="jobs-state">Dobieranie zleceń…</div>}
          {!recommendationsLoading && recommendationsError && (
            <div className="jobs-state jobs-state--error">{recommendationsError}</div>
          )}
          {!recommendationsLoading && !recommendationsError && recommendations?.specializationCount === 0 && (
            <div className="jobs-state jobs-recommended__empty">
              <strong>Uzupełnij specjalizacje, aby otrzymywać rekomendacje.</strong>
              <span>Wybierz do 10 konkretnych usług na swoim profilu.</span>
              <Link className="button button--primary" to="/profile">Ustaw specjalizacje</Link>
            </div>
          )}
          {!recommendationsLoading
            && !recommendationsError
            && recommendations?.specializationCount > 0
            && recommendedJobs.length === 0 && (
              <div className="jobs-state">
                Obecnie nie ma otwartych zleceń w Twoich specjalizacjach. Nowe oferty pojawią się tutaj automatycznie.
              </div>
            )}

          {!recommendationsLoading && !recommendationsError && recommendedJobs.length > 0 && (
            <div className="jobs-grid jobs-grid--recommended">
              {recommendedJobs.map((job) => (
                <JobCard
                  key={job.id}
                  job={job}
                  initialSaved={savedJobIds.has(job.id)}
                  onSavedChange={updateSavedState}
                />
              ))}
            </div>
          )}
        </section>
      )}

      <form className="jobs-filters" onSubmit={applyFilters}>
        <label className="jobs-filters__search">
          <span>Szukaj</span>
          <input
            type="search"
            name="query"
            value={draftFilters.query}
            onChange={updateDraft}
            placeholder="np. zakupy, paczka, Plac Grunwaldzki"
            maxLength={100}
          />
        </label>

        <label className="jobs-filters__category">
          <span>Kategoria</span>
          <select name="category" value={draftFilters.category} onChange={updateDraft}>
            <option value="">Wszystkie kategorie</option>
            {categories.map((category) => (
              <optgroup key={category.id} label={category.name}>
                <option value={category.slug}>Wszystkie: {category.name}</option>
                {category.children.map((child) => (
                  <option key={child.id} value={child.slug}>{child.name}</option>
                ))}
              </optgroup>
            ))}
          </select>
        </label>

        <label>
          <span>Od</span>
          <input
            type="number"
            name="minPrice"
            value={draftFilters.minPrice}
            onChange={updateDraft}
            min="0"
            step="0.01"
            placeholder="0 zł"
          />
        </label>

        <label>
          <span>Do</span>
          <input
            type="number"
            name="maxPrice"
            value={draftFilters.maxPrice}
            onChange={updateDraft}
            min="0"
            step="0.01"
            placeholder="bez limitu"
          />
        </label>

        <div className="jobs-filters__actions">
          <button type="submit" className="button button--primary">Filtruj</button>
          <button type="button" className="button button--secondary" onClick={clearFilters}>Wyczyść</button>
        </div>
      </form>

      {user && (
        <section className="saved-search-create" aria-labelledby="saved-search-create-title">
          <div>
            <span className="jobs-hero__badge">Preset</span>
            <h2 id="saved-search-create-title">Zapisz obecne filtry</h2>
            <p>
              Zachowaj filtry do ponownego użycia i opcjonalnie ogranicz alerty do prywatnego obszaru wokół wybranego punktu.
            </p>
          </div>
          <form className="saved-search-create__form" onSubmit={saveCurrentSearch}>
            <label>
              <span>Nazwa wyszukiwania</span>
              <input
                type="text"
                value={savedSearchName}
                onChange={(event) => setSavedSearchName(event.target.value)}
                maxLength={80}
                placeholder="np. Przeprowadzki 100–500 zł"
                required
              />
            </label>

            <label className="saved-search-create__location-toggle">
              <input
                type="checkbox"
                checked={limitSavedSearchByLocation}
                onChange={(event) => setLimitSavedSearchByLocation(event.target.checked)}
              />
              <span>Ogranicz alerty do wybranej okolicy</span>
            </label>

            {limitSavedSearchByLocation && (
              <div className="saved-search-create__location">
                <LocationMapPicker
                  location={savedSearchLocation}
                  onLocationChange={setSavedSearchLocation}
                  disabled={savingSearch}
                />
                <label>
                  <span>Promień alertu</span>
                  <select
                    value={savedSearchRadiusKm}
                    onChange={(event) => setSavedSearchRadiusKm(event.target.value)}
                    disabled={savingSearch}
                  >
                    <option value="1">1 km</option>
                    <option value="2">2 km</option>
                    <option value="5">5 km</option>
                    <option value="10">10 km</option>
                    <option value="20">20 km</option>
                    <option value="30">30 km</option>
                    <option value="50">50 km</option>
                    <option value="100">100 km</option>
                  </select>
                </label>
                <p className="saved-search-create__hint">
                  Do presetu wysyłamy tylko współrzędne punktu i promień. Dokładny adres użyty przez picker nie trafia do saved search ani do treści powiadomień.
                </p>
              </div>
            )}

            <button
              className="button button--primary"
              type="submit"
              disabled={savingSearch || !canSaveSearch}
            >
              {savingSearch ? 'Zapisywanie…' : 'Zapisz wyszukiwanie'}
            </button>
            <Link className="button button--secondary saved-search-create__link" to="/saved-searches">
              Moje wyszukiwania
            </Link>
          </form>
          {!canSaveSearch && (
            <p className="saved-search-create__hint">
              Ustaw i zastosuj co najmniej jeden filtr albo wybierz prawidłowy obszar alertu.
            </p>
          )}
          {savedSearchMessage && <p className="saved-search-create__success">{savedSearchMessage}</p>}
          {savedSearchError && <p className="saved-search-create__error" role="alert">{savedSearchError}</p>}
        </section>
      )}

      <section className="jobs-results" aria-live="polite">
        <div className="jobs-results__heading">
          <div>
            <h2>Otwarte zlecenia</h2>
            {result && <p>{result.totalElements} dostępnych zleceń</p>}
          </div>
        </div>

        {loading && <div className="jobs-state">Pobieranie zleceń…</div>}
        {!loading && error && <div className="jobs-state jobs-state--error">{error}</div>}
        {!loading && !error && jobs.length === 0 && (
          <div className="jobs-state">Brak zleceń pasujących do tych filtrów.</div>
        )}

        {!loading && !error && jobs.length > 0 && (
          <div className="jobs-grid">
            {jobs.map((job) => (
              <JobCard
                key={job.id}
                job={job}
                initialSaved={savedJobIds.has(job.id)}
                onSavedChange={updateSavedState}
              />
            ))}
          </div>
        )}

        {!loading && !error && result && result.totalPages > 1 && (
          <nav className="jobs-pagination" aria-label="Paginacja zleceń">
            <button
              type="button"
              className="button button--secondary"
              disabled={result.first}
              onClick={() => changePage(result.page - 1)}
            >
              Poprzednia
            </button>
            <span>Strona {result.page + 1} z {result.totalPages}</span>
            <button
              type="button"
              className="button button--secondary"
              disabled={result.last}
              onClick={() => changePage(result.page + 1)}
            >
              Następna
            </button>
          </nav>
        )}
      </section>
    </main>
  )
}

function hasValidSavedSearchLocation(location, radiusKm) {
  if (!location) return false
  const latitude = Number(location.latitude)
  const longitude = Number(location.longitude)
  return Number.isFinite(latitude)
    && latitude >= -90
    && latitude <= 90
    && Number.isFinite(longitude)
    && longitude >= -180
    && longitude <= 180
    && Number.isInteger(radiusKm)
    && radiusKm >= 1
    && radiusKm <= 100
}

export default JobsPage
