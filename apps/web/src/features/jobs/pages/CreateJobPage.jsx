import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import JobAssignmentModePicker from '../components/JobAssignmentModePicker.jsx'
import JobPublicationPaymentPanel from '../components/JobPublicationPaymentPanel.jsx'
import LocationMapPicker from '../components/LocationMapPicker.jsx'
import RouteMapPicker from '../components/RouteMapPicker.jsx'
import { createJobPublication, createRouteQuote, getJobCategories, getPendingJobPublications, getRouteModeEstimates } from '../api/jobsApi.js'
import './CreateJobPage.css'

const EMPTY_FORM = {
  title: '',
  description: '',
  price: '',
  categoryId: '',
  assignmentMode: 'INSTANT',
  priceNegotiationEnabled: false,
}
const MAX_STOPS = 10

function createPublicationRequestId() {
  if (window.crypto?.randomUUID) return `jobpub_${window.crypto.randomUUID()}`
  return `jobpub_${Date.now()}_${Math.random().toString(36).slice(2)}`
}

function CreateJobPage() {
  const navigate = useNavigate()
  const requestIdRef = useRef(createPublicationRequestId())
  const [form, setForm] = useState(EMPTY_FORM)
  const [categories, setCategories] = useState([])
  const [categoriesLoading, setCategoriesLoading] = useState(true)
  const [location, setLocation] = useState(null)
  const [origin, setOrigin] = useState(null)
  const [stops, setStops] = useState([])
  const [destination, setDestination] = useState(null)
  const [routeQuote, setRouteQuote] = useState(null)
  const [modeComparison, setModeComparison] = useState(null)
  const [modesLoading, setModesLoading] = useState(false)
  const [routing, setRouting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [publication, setPublication] = useState(null)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [routeError, setRouteError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getPendingJobPublications({ signal: controller.signal })
      .then((pending) => {
        if (pending?.length > 0) setPublication(pending[0])
      })
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') {
          setError(requestError.message || 'Nie udało się sprawdzić rozpoczętej publikacji.')
        }
      })
    return () => controller.abort()
  }, [])

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

  const selectableGroups = useMemo(() => categories
    .map((group) => ({ ...group, children: (group.children || []).filter((child) => child.fulfillmentMode) }))
    .filter((group) => group.children.length > 0), [categories])

  const selectedCategory = useMemo(() => {
    const selectedId = Number(form.categoryId)
    if (!selectedId) return null
    for (const group of selectableGroups) {
      const match = group.children.find((category) => category.id === selectedId)
      if (match) return match
    }
    return null
  }, [form.categoryId, selectableGroups])

  const fulfillmentMode = selectedCategory?.fulfillmentMode || null

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

  const updateOnSiteLocation = useCallback((point) => {
    setLocation(point)
    setError('')
  }, [])

  const addStop = useCallback(() => {
    setStops((current) => (current.length >= MAX_STOPS ? current : [...current, null]))
    invalidateRoute()
  }, [invalidateRoute])

  const removeStop = useCallback((index) => {
    setStops((current) => current.filter((_, currentIndex) => currentIndex !== index))
    invalidateRoute()
  }, [invalidateRoute])

  useEffect(() => {
    if (fulfillmentMode !== 'POINT_TO_POINT' || publication) return undefined
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
  }, [destination, fulfillmentMode, origin, publication, stops])

  useEffect(() => {
    if (!routeQuote?.id || fulfillmentMode !== 'POINT_TO_POINT') {
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
  }, [fulfillmentMode, routeQuote?.id])

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  function updateCategory(event) {
    const value = event.target.value
    setForm((current) => ({ ...current, categoryId: value }))
    setLocation(null)
    setOrigin(null)
    setStops([])
    setDestination(null)
    setRouting(false)
    invalidateRoute()
    setError('')
  }

  function updateAssignmentMode(assignmentMode) {
    setForm((current) => ({
      ...current,
      assignmentMode,
      priceNegotiationEnabled: assignmentMode === 'PROPOSALS' ? current.priceNegotiationEnabled : false,
    }))
  }

  function updatePriceNegotiationEnabled(priceNegotiationEnabled) {
    setForm((current) => ({ ...current, priceNegotiationEnabled }))
  }

  function resetPublicationFlow(message) {
    requestIdRef.current = createPublicationRequestId()
    setPublication(null)
    setError('')
    setInfo(message || '')
    if (fulfillmentMode === 'POINT_TO_POINT') {
      setRouteQuote(null)
      setModeComparison(null)
      setDestination((current) => (current ? { ...current } : current))
    }
  }

  async function submit(event) {
    event.preventDefault()
    if (!selectedCategory) {
      setError('Wybierz konkretną kategorię usługi.')
      return
    }
    if (fulfillmentMode === 'ON_SITE' && !isCompletePoint(location)) {
      setError('Wskaż dokładne miejsce wykonania usługi.')
      return
    }
    if (fulfillmentMode === 'POINT_TO_POINT' && !routeQuote?.id) {
      setError('Najpierw uzupełnij całą trasę i poczekaj na jej wyznaczenie.')
      return
    }

    setSubmitting(true)
    setError('')
    setInfo('')
    try {
      const payload = {
        title: form.title,
        description: form.description,
        price: Number(form.price),
        categoryId: Number(form.categoryId),
        assignmentMode: form.assignmentMode,
        priceNegotiationEnabled: form.assignmentMode === 'PROPOSALS' && form.priceNegotiationEnabled,
      }
      if (fulfillmentMode === 'ON_SITE') payload.location = normalizePoint(location)
      else payload.routeQuoteId = routeQuote.id

      const result = await createJobPublication(payload, requestIdRef.current)
      if (result.status === 'PUBLISHED' && result.jobId) {
        navigate('/my-jobs')
        return
      }
      if (result.status === 'PAYMENT_REQUIRED') {
        setPublication(result)
        return
      }
      if (result.status === 'PAYMENT_RECEIVED') {
        resetPublicationFlow('Płatność jest już w Twoim portfelu. Przygotuj publikację ponownie z aktualnym saldem.')
        return
      }
      if (result.status === 'CANCELLED') {
        resetPublicationFlow('Poprzednia próba publikacji została anulowana. Możesz spróbować ponownie.')
        return
      }
      setError('Nie udało się ustalić stanu publikacji.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się przygotować publikacji zlecenia.')
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

  const locationReady = fulfillmentMode === 'ON_SITE'
    ? isCompletePoint(location)
    : fulfillmentMode === 'POINT_TO_POINT' && Boolean(routeQuote?.id)

  return (
    <main className="create-job-page">
      <header className="page-heading">
        <span className="eyebrow">Nowe zlecenie</span>
        <h1>{publication ? 'Dokończ finansowanie' : (fulfillmentMode === 'ON_SITE' ? 'Gdzie ma być wykonane?' : 'Skąd i dokąd?')}</h1>
        <p>{publication
          ? 'Formularz jest zamrożony dla tej próby publikacji. Zlecenie nie jest jeszcze publiczne.'
          : (fulfillmentMode === 'ON_SITE'
            ? 'Wskaż miejsce wykonania usługi. Publicznie pokażemy tylko obszar, a dokładny adres dostanie wykonawca po przyjęciu zlecenia.'
            : 'Dla zleceń transportowych wybierz A i B, a w razie potrzeby dodaj do 10 przystanków. Dokładne adresy są widoczne wyłącznie dla stron zlecenia.')}</p>
      </header>

      {publication ? (
        <JobPublicationPaymentPanel
          publication={publication}
          onPublished={() => navigate('/my-jobs')}
          onReset={resetPublicationFlow}
        />
      ) : (
        <form className="panel create-job-form" onSubmit={submit}>
          <label className="field create-job-form__wide">
            <span>Kategoria usługi</span>
            <select name="categoryId" value={form.categoryId} onChange={updateCategory} required disabled={categoriesLoading || submitting}>
              <option value="">{categoriesLoading ? 'Ładowanie kategorii…' : 'Wybierz podkategorię'}</option>
              {selectableGroups.map((group) => (
                <optgroup key={group.id} label={group.name}>
                  {group.children.map((category) => (
                    <option key={category.id} value={category.id}>{category.name}</option>
                  ))}
                </optgroup>
              ))}
            </select>
            <small>{selectedCategory
              ? (fulfillmentMode === 'ON_SITE' ? 'Ta usługa jest wykonywana w jednym miejscu.' : 'Ta usługa wymaga przejazdu pomiędzy punktami.')
              : 'Typ lokalizacji dobierzemy automatycznie na podstawie wybranej usługi.'}</small>
          </label>

          {!selectedCategory && (
            <div className="create-job-form__wide route-summary">Wybierz kategorię, aby wskazać miejsce lub trasę realizacji.</div>
          )}

          {fulfillmentMode === 'ON_SITE' && (
            <div className="create-job-form__wide">
              <LocationMapPicker location={location} onLocationChange={updateOnSiteLocation} disabled={submitting} />
            </div>
          )}

          {fulfillmentMode === 'POINT_TO_POINT' && (
            <>
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
            </>
          )}

          <label className="field create-job-form__wide">
            <span>Tytuł</span>
            <input name="title" value={form.title} onChange={updateField} minLength={3} maxLength={160} placeholder={fulfillmentMode === 'ON_SITE' ? 'np. Zmontuj szafę w mieszkaniu' : 'np. Odbierz dwie paczki po drodze i dowieź do B'} required />
          </label>
          <label className="field create-job-form__wide">
            <span>Opis</span>
            <textarea name="description" value={form.description} onChange={updateField} minLength={10} maxLength={4000} rows={6} placeholder={fulfillmentMode === 'ON_SITE' ? 'Opisz dokładnie, co trzeba zrobić na miejscu i jakie narzędzia mogą być potrzebne.' : 'Opisz co trzeba odebrać lub zrobić w każdym punkcie i wszystkie ważne szczegóły.'} required />
          </label>
          <label className="field">
            <span>Wynagrodzenie / budżet</span>
            <input name="price" type="number" value={form.price} onChange={updateField} min="0.01" step="0.01" placeholder="25.00" required />
            <small>Najpierw użyjemy dostępnego salda. Jeśli go zabraknie, zapłacisz tylko brakującą kwotę przez Stripe.</small>
          </label>

          <div className="create-job-form__wide">
            <JobAssignmentModePicker
              assignmentMode={form.assignmentMode}
              priceNegotiationEnabled={form.priceNegotiationEnabled}
              price={form.price}
              disabled={submitting}
              onModeChange={updateAssignmentMode}
              onNegotiationChange={updatePriceNegotiationEnabled}
            />
          </div>

          <div className="create-job-form__wide create-job-form__actions">
            <span className="create-job-form__privacy">{fulfillmentMode === 'ON_SITE'
              ? 'Dokładny adres jest chroniony. Publiczne ogłoszenie pokazuje tylko obszar.'
              : 'Dokładne A, przystanki i B są chronione. Publiczne ogłoszenie nie zawiera współrzędnych ani numerów adresów.'}</span>
            <button className="button button--primary" type="submit" disabled={submitting || routing || !selectedCategory || !locationReady}>
              {submitting ? 'Sprawdzamy finansowanie…' : 'Opublikuj zlecenie'}
            </button>
          </div>
          {info && <div className="form-message form-message--success create-job-form__wide">{info}</div>}
          {error && <div className="form-message form-message--error create-job-form__wide">{error}</div>}
        </form>
      )}
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