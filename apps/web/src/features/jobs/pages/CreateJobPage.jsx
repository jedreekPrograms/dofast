import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import RouteMapPicker from '../components/RouteMapPicker.jsx'
import SingleLocationPicker from '../components/SingleLocationPicker.jsx'
import { createJob, createRouteQuote, getJobCategories, getRouteModeEstimates } from '../api/jobsApi.js'
import './CreateJobPage.css'

const EMPTY_FORM = { title: '', description: '', price: '', categoryId: '' }

function CreateJobPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState(EMPTY_FORM)
  const [categories, setCategories] = useState([])
  const [categoriesLoading, setCategoriesLoading] = useState(true)
  const [origin, setOrigin] = useState(null)
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

  const categoryGroups = useMemo(() => categories
    .map((group) => ({ ...group, children: (group.children || []).filter((child) => child.fulfillmentMode) }))
    .filter((group) => group.children.length > 0), [categories])

  const selectedCategory = useMemo(() => categoryGroups
    .flatMap((group) => group.children)
    .find((category) => String(category.id) === String(form.categoryId)) || null, [categoryGroups, form.categoryId])

  const isOnSite = selectedCategory?.fulfillmentMode === 'ON_SITE'
  const isPointToPoint = selectedCategory?.fulfillmentMode === 'POINT_TO_POINT'

  const updatePoint = useCallback((kind, point) => {
    if (kind === 'origin') setOrigin(point)
    else setDestination(point)
    setRouteQuote(null)
    setModeComparison(null)
    setRouteError('')
  }, [])

  const updateOnSiteLocation = useCallback((point) => {
    setOrigin(point)
    setDestination(null)
    setRouteQuote(null)
    setModeComparison(null)
    setRouteError('')
  }, [])

  useEffect(() => {
    if (!isPointToPoint || !isCompletePoint(origin) || !isCompletePoint(destination)) {
      setRouting(false)
      setRouteQuote(null)
      setModeComparison(null)
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
  }, [destination, isPointToPoint, origin])

  useEffect(() => {
    if (!isPointToPoint || !routeQuote?.id) {
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
  }, [isPointToPoint, routeQuote?.id])

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
    if (name === 'categoryId') {
      setOrigin(null)
      setDestination(null)
      setRouteQuote(null)
      setModeComparison(null)
      setRouteError('')
    }
  }

  async function submit(event) {
    event.preventDefault()
    if (!selectedCategory) {
      setError('Wybierz konkretną kategorię usługi.')
      return
    }
    if (isPointToPoint && !routeQuote?.id) {
      setError('Najpierw wybierz poprawny punkt A i B oraz poczekaj na wyznaczenie trasy.')
      return
    }
    if (isOnSite && !isCompletePoint(origin)) {
      setError('Wybierz dokładne miejsce wykonania usługi.')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      const payload = {
        title: form.title,
        description: form.description,
        price: Number(form.price),
        categoryId: Number(form.categoryId),
      }
      if (isPointToPoint) payload.routeQuoteId = routeQuote.id
      if (isOnSite) payload.location = normalizePoint(origin)
      await createJob(payload)
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

  const locationReady = isPointToPoint ? Boolean(routeQuote?.id) : isOnSite ? isCompletePoint(origin) : false

  return (
    <main className="create-job-page">
      <header className="page-heading">
        <span className="eyebrow">Nowe zlecenie</span>
        <h1>{isOnSite ? 'Gdzie wykonać usługę?' : 'Skąd i dokąd?'}</h1>
        <p>{isOnSite
          ? 'Wybierz rodzaj usługi i dokładne miejsce wykonania. Publicznie pokażemy tylko obszar; pełny adres dostanie przypisany wykonawca podczas aktywnego zlecenia.'
          : 'Wybierz rodzaj usługi oraz punkt A i B. Publicznie pokazujemy tylko obszary; dokładne adresy dostanie przypisany wykonawca dopiero podczas aktywnego zlecenia.'}</p>
      </header>

      <form className="panel create-job-form" onSubmit={submit}>
        <label className="field create-job-form__wide">
          <span>Kategoria usługi</span>
          <select name="categoryId" value={form.categoryId} onChange={updateField} required disabled={categoriesLoading || submitting}>
            <option value="">{categoriesLoading ? 'Ładowanie kategorii…' : 'Wybierz podkategorię'}</option>
            {categoryGroups.map((group) => (
              <optgroup key={group.id} label={group.name}>
                {group.children.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}{category.fulfillmentMode === 'ON_SITE' ? ' · na miejscu' : ' · transport A→B'}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
          <small>Typ lokalizacji jest dobierany automatycznie do wybranej podkategorii.</small>
        </label>

        {isOnSite && (
          <div className="create-job-form__wide">
            <SingleLocationPicker value={origin} onChange={updateOnSiteLocation} disabled={submitting} />
          </div>
        )}

        {isPointToPoint && (
          <div className="create-job-form__wide">
            <RouteMapPicker
              origin={origin}
              destination={destination}
              routeQuote={routeQuote}
              onPointChange={updatePoint}
              disabled={submitting}
            />
          </div>
        )}

        {!selectedCategory && !categoriesLoading && (
          <div className="create-job-form__wide route-summary">Wybierz kategorię, aby dopasować sposób podania lokalizacji.</div>
        )}

        {isPointToPoint && (
          <div className="create-job-form__wide route-summary" aria-live="polite">
            {routing && <span>Wyznaczanie trasy i czasu dojazdu…</span>}
            {!routing && routeQuote && (
              <>
                <strong>{routeQuote.origin.publicLabel} → {routeQuote.destination.publicLabel}</strong>
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
                {modeComparison?.nonDrivingBetaWarningRequired && (
                  <small>Trasy piesze i rowerowe Google są w wersji beta i mogą nie uwzględniać wszystkich chodników, ścieżek lub warunków terenowych.</small>
                )}
                {routeQuote.provider === 'DETERMINISTIC_DEV' && <small>Tryb deweloperski — prawdziwe ETA Google pojawi się po konfiguracji Routes API.</small>}
              </>
            )}
            {routeError && <span className="form-message form-message--error">{routeError}</span>}
          </div>
        )}

        {isOnSite && origin && (
          <div className="create-job-form__wide route-summary" aria-live="polite">
            <strong>{origin.publicLabel}</strong>
            <small>Pełny adres jest prywatny i nie pojawi się w publicznym ogłoszeniu.</small>
          </div>
        )}

        <label className="field create-job-form__wide">
          <span>Tytuł</span>
          <input name="title" value={form.title} onChange={updateField} minLength={3} maxLength={160} placeholder={isOnSite ? 'np. Zamontuj półkę w salonie' : 'np. Odbierz paczkę z punktu A i dowieź do B'} required />
        </label>
        <label className="field create-job-form__wide">
          <span>Opis</span>
          <textarea name="description" value={form.description} onChange={updateField} minLength={10} maxLength={4000} rows={6} placeholder={isOnSite ? 'Opisz zakres pracy i wszystkie ważne szczegóły.' : 'Opisz co trzeba odebrać, komu przekazać i wszystkie ważne szczegóły.'} required />
        </label>
        <label className="field">
          <span>Wynagrodzenie</span>
          <input name="price" type="number" value={form.price} onChange={updateField} min="0.01" step="0.01" placeholder="25.00" required />
        </label>

        <div className="create-job-form__wide create-job-form__actions">
          <span className="create-job-form__privacy">Dokładne adresy są chronione. Publiczne ogłoszenie nie zawiera współrzędnych ani numerów adresów.</span>
          <button className="button button--primary" type="submit" disabled={submitting || routing || !selectedCategory || !locationReady}>
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
