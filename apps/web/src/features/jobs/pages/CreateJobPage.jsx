import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import RouteMapPicker from '../components/RouteMapPicker.jsx'
import { createJob, createRouteQuote, getJobCategories, getRouteModeEstimates } from '../api/jobsApi.js'
import './CreateJobPage.css'

const EMPTY_FORM = { title: '', description: '', price: '', categoryId: '' }
const MAX_STOPS = 10

function CreateJobPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState(EMPTY_FORM)
  const [categories, setCategories] = useState([])
  const [categoriesLoading, setCategoriesLoading] = useState(true)
  const [origin, setOrigin] = useState(null)
  const [stops, setStops] = useState([])
  const [destination, setDestination] = useState(null)
  const [routeQuote, setRouteQuote] = useState(null)
  const [modeComparison, setModeComparison] = useState(null)
  const [modesLoading, setModesLoading] = useState(false)
  const [routing, setRouting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [routeError, setRouteError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    setCategoriesLoading(true)
    getJobCategories({ signal: controller.signal })
      .then(setCategories)
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') {
          setError(requestError.message || 'Nie udało się pobrać kategorii usług.')
        }
      })
      .finally(() => setCategoriesLoading(false))
    return () => controller.abort()
  }, [])

  const pointToPointGroups = useMemo(() => categories
    .map((group) => ({
      ...group,
      children: (group.children || []).filter((child) => child.fulfillmentMode === 'POINT_TO_POINT'),
    }))
    .filter((group) => group.children.length > 0), [categories])

  const invalidateRoute = useCallback(() => {
    setRouteQuote(null)
    setModeComparison(null)
    setRouteError('')
  }, [])

  const updatePoint = useCallback((kind, point) => {
    if (kind === 'origin') {
      setOrigin(point)
    } else if (kind === 'destination') {
      setDestination(point)
    } else {
      const index = stopKindIndex(kind)
      if (index !== null) {
        setStops((current) => current.map((value, currentIndex) => (currentIndex === index ? point : value)))
      }
    }
    invalidateRoute()
  }, [invalidateRoute])

  const addStop = useCallback(() => {
    setStops((current) => (current.length >= MAX_STOPS ? current : [...current, null]))
    invalidateRoute()
  }, [invalidateRoute])

  const removeStop = useCallback((index) => {
    setStops((current) => current.filter((_, currentIndex) => currentIndex !== index))
    invalidateRoute()
  }, [invalidateRoute])

  useEffect(() => {
    if (!isCompletePoint(origin)
      || !isCompletePoint(destination)
      || !stops.every(isCompletePoint)) {
      setRouting(false)
      return undefined
    }

    let cancelled = false
    const timer = window.setTimeout(async () => {
      setRouting(true)
      setRouteError('')
      setModeComparison(null)
      try {
        const quote = await createRouteQuote({
          origin: normalizePoint(origin),
          stops: stops.map(normalizePoint),
          destination: normalizePoint(destination),
        })
        if (!cancelled) setRouteQuote(quote)
      } catch (requestError) {
        if (!cancelled) {
          setRouteQuote(null)
          setRouteError(requestError.message || 'Nie udało się wyznaczyć trasy.')
        }
      } finally {
        if (!cancelled) setRouting(false)
      }
    }, 500)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [destination, origin, stops])

  useEffect(() => {
    if (!routeQuote?.id) {
      setModesLoading(false)
      setModeComparison(null)
      return undefined
    }

    const controller = new AbortController()
    setModesLoading(true)
    getRouteModeEstimates(routeQuote.id, { signal: controller.signal })
      .then(setModeComparison)
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setModeComparison(null)
      })
      .finally(() => setModesLoading(false))
    return () => controller.abort()
  }, [routeQuote?.id])

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    if (!form.categoryId) {
      setError('Wybierz konkretną kategorię usługi.')
      return
    }
    if (!routeQuote?.id) {
      setError('Najpierw uzupełnij całą trasę i poczekaj na jej wyznaczenie.')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      await createJob({
        title: form.title,
        description: form.description,
        price: Number(form.price),
        categoryId: Number(form.categoryId),
        routeQuoteId: routeQuote.id,
      })
      navigate('/my-jobs')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się opublikować zlecenia.')
    } finally {
      setSubmitting(false)
    }
  }

  const modeEstimates = modeComparison?.estimates || (routeQuote ? [{
    mode: 'DRIVE',
    distanceMeters: routeQuote.distanceMeters,
    durationSeconds: routeQuote.durationSeconds,
    available: true,
  }] : [])

  return (
    <main className="create-job-page">
      <header className="page-heading">
        <span className="eyebrow">Nowe zlecenie</span>
        <h1>Skąd i dokąd?</h1>
        <p>Wybierz punkt A i B, a jeśli zadanie wymaga kilku odbiorów lub czynności po drodze, dodaj do 10 przystanków. Dokładne adresy są widoczne wyłącznie dla stron zlecenia.</p>
      </header>

      <form className="panel create-job-form" onSubmit={submit}>
        <label className="field create-job-form__wide">
          <span>Kategoria usługi</span>
          <select name="categoryId" value={form.categoryId} onChange={updateField} required disabled={categoriesLoading || submitting}>
            <option value="">{categoriesLoading ? 'Ładowanie kategorii…' : 'Wybierz podkategorię'}</option>
            {pointToPointGroups.map((group) => (
              <optgroup key={group.id} label={group.name}>
                {group.children.map((category) => (
                  <option key={category.id} value={category.id}>{category.name}</option>
                ))}
              </optgroup>
            ))}
          </select>
          <small>Przystanki sprawdzą się np. przy odbiorze rzeczy z kilku miejsc, zakupach w kilku punktach albo trasie wymagającej dodatkowej wizyty po drodze.</small>
        </label>

        <div className="create-job-form__wide">
          <RouteMapPicker
            origin={origin}
            stops={stops}
            destination={destination}
            routeQuote={routeQuote}
            onPointChange={updatePoint}
            onAddStop={addStop}
            onRemoveStop={removeStop}
            disabled={submitting}
          />
        </div>

        <div className="create-job-form__wide route-summary" aria-live="polite">
          {routing && <span>Wyznaczanie całej trasy i czasu dojazdu…</span>}
          {!routing && routeQuote && (
            <>
              <strong>{routeLabel(routeQuote)}</strong>
              <div className="route-mode-grid">
                {modeEstimates.map((estimate) => (
                  <div className="route-mode-card" key={estimate.mode}>
                    <span>{modeLabel(estimate.mode)}</span>
                    {estimate.available ? (
                      <strong>{formatDuration(estimate.durationSeconds)} · {formatDistance(estimate.distanceMeters)}</strong>
                    ) : (
                      <strong>Niedostępna</strong>
                    )}
                  </div>
                ))}
                {modesLoading && modeEstimates.length === 1 && (
                  <div className="route-mode-card route-mode-card--loading">Liczenie roweru i pieszo…</div>
                )}
              </div>
              {routeQuote.stops?.length > 0 && <small>Trasa obejmuje {routeQuote.stops.length} {routeQuote.stops.length === 1 ? 'przystanek' : 'przystanki/przystanków'} w podanej kolejności.</small>}
              {modeComparison?.nonDrivingBetaWarningRequired && (
                <small>Trasy piesze i rowerowe Google są w wersji beta i mogą nie uwzględniać wszystkich chodników, ścieżek lub warunków terenowych.</small>
              )}
              {routeQuote.provider === 'DETERMINISTIC_DEV' && <small>Tryb deweloperski — prawdziwe ETA Google pojawi się po konfiguracji Routes API.</small>}
            </>
          )}
          {routeError && <span className="form-message form-message--error">{routeError}</span>}
        </div>

        <label className="field create-job-form__wide">
          <span>Tytuł</span>
          <input name="title" value={form.title} onChange={updateField} minLength={3} maxLength={160} placeholder="np. Odbierz dwie paczki po drodze i dowieź do B" required />
        </label>
        <label className="field create-job-form__wide">
          <span>Opis</span>
          <textarea name="description" value={form.description} onChange={updateField} minLength={10} maxLength={4000} rows={6} placeholder="Opisz co trzeba odebrać lub zrobić w każdym punkcie i wszystkie ważne szczegóły." required />
        </label>
        <label className="field">
          <span>Wynagrodzenie</span>
          <input name="price" type="number" value={form.price} onChange={updateField} min="0.01" step="0.01" placeholder="25.00" required />
        </label>

        <div className="create-job-form__wide create-job-form__actions">
          <span className="create-job-form__privacy">Dokładne A, przystanki i B są chronione. Publiczne ogłoszenie nie zawiera współrzędnych ani numerów adresów.</span>
          <button className="button button--primary" type="submit" disabled={submitting || routing || !routeQuote?.id || !form.categoryId}>
            {submitting ? 'Publikowanie…' : 'Opublikuj zlecenie'}
          </button>
        </div>
        {error && <div className="form-message form-message--error create-job-form__wide">{error}</div>}
      </form>
    </main>
  )
}

function isCompletePoint(point) {
  if (!point) return false
  const latitude = Number(point.latitude)
  const longitude = Number(point.longitude)
  return Number.isFinite(latitude)
    && latitude >= -90 && latitude <= 90
    && Number.isFinite(longitude)
    && longitude >= -180 && longitude <= 180
    && Boolean(point.publicLabel?.trim())
    && Boolean(point.privateLabel?.trim())
}

function normalizePoint(point) {
  return {
    latitude: Number(point.latitude),
    longitude: Number(point.longitude),
    publicLabel: point.publicLabel.trim(),
    privateLabel: point.privateLabel.trim(),
    placeId: point.placeId || null,
  }
}

function stopKindIndex(kind) {
  if (!kind?.startsWith('stop-')) return null
  const index = Number(kind.slice(5))
  return Number.isInteger(index) && index >= 0 ? index : null
}

function routeLabel(routeQuote) {
  return [routeQuote.origin, ...(routeQuote.stops || []), routeQuote.destination]
    .map((point) => point.publicLabel)
    .join(' → ')
}

function modeLabel(mode) {
  if (mode === 'BICYCLE') return 'Rowerem'
  if (mode === 'WALK') return 'Pieszo'
  return 'Samochodem'
}

function formatDistance(meters) {
  if (meters < 1000) return `${meters} m`
  return `${(meters / 1000).toFixed(meters < 10_000 ? 1 : 0)} km`
}

function formatDuration(seconds) {
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  return remainder ? `${hours} godz. ${remainder} min` : `${hours} godz.`
}

export default CreateJobPage
