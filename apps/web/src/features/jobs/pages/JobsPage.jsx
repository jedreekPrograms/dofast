import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import JobCard from '../components/JobCard.jsx'
import {
  createSavedSearch,
  getJobCategories,
  getJobs,
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
  const [savedJobIds, setSavedJobIds] = useState(() => new Set())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [savedSearchName, setSavedSearchName] = useState('')
  const [savingSearch, setSavingSearch] = useState(false)
  const [savedSearchMessage, setSavedSearchMessage] = useState('')
  const [savedSearchError, setSavedSearchError] = useState('')

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
        let nextSavedJobIds = new Set()

        if (userId && data.content?.length) {
          try {
            const statuses = await getSavedJobStatuses(
              data.content.map((job) => job.id),
              { signal: controller.signal },
            )
            nextSavedJobIds = new Set(statuses.savedJobIds ?? [])
          } catch (requestError) {
            if (requestError.name === 'AbortError') throw requestError
          }
        }

        setSavedJobIds(nextSavedJobIds)
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
  }, [filters, userId])

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

    try {
      await createSavedSearch({
        name: savedSearchName,
        query: filters.query?.trim() || null,
        categorySlug: filters.category || null,
        minPrice: filters.minPrice === '' ? null : Number(filters.minPrice),
        maxPrice: filters.maxPrice === '' ? null : Number(filters.maxPrice),
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
  const hasActiveFilters = Boolean(
    filters.query?.trim()
      || filters.category
      || filters.minPrice !== ''
      || filters.maxPrice !== '',
  )

  return (
    <main className="jobs-page">
      <header className="jobs-hero">
        <span className="jobs-hero__badge">Zlecenia lokalne</span>
        <h1>Znajdź zlecenie blisko siebie</h1>
        <p>
          Przeglądaj aktualne zadania i filtruj je po kategorii, cenie, nazwie, opisie lub obszarze.
        </p>
      </header>

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
              Zachowaj tę kombinację filtrów do ponownego użycia. Nie zapisujemy dokładnej lokalizacji ani prywatnych adresów.
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
            <button
              className="button button--primary"
              type="submit"
              disabled={savingSearch || !hasActiveFilters}
            >
              {savingSearch ? 'Zapisywanie…' : 'Zapisz wyszukiwanie'}
            </button>
            <Link className="button button--secondary saved-search-create__link" to="/saved-searches">
              Moje wyszukiwania
            </Link>
          </form>
          {!hasActiveFilters && (
            <p className="saved-search-create__hint">Ustaw i zastosuj co najmniej jeden filtr, aby zapisać wyszukiwanie.</p>
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

export default JobsPage
