import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import RouteMapPicker from '../components/RouteMapPicker.jsx'
import { createJob, createRouteQuote } from '../api/jobsApi.js'
import './CreateJobPage.css'

const EMPTY_FORM = { title: '', description: '', price: '' }

function CreateJobPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState(EMPTY_FORM)
  const [origin, setOrigin] = useState(null)
  const [destination, setDestination] = useState(null)
  const [routeQuote, setRouteQuote] = useState(null)
  const [routing, setRouting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [routeError, setRouteError] = useState('')

  const updatePoint = useCallback((kind, point) => {
    if (kind === 'origin') setOrigin(point)
    else setDestination(point)
    setRouteQuote(null)
    setRouteError('')
  }, [])

  useEffect(() => {
    if (!isCompletePoint(origin) || !isCompletePoint(destination)) {
      setRouting(false)
      return undefined
    }

    let cancelled = false
    const timer = window.setTimeout(async () => {
      setRouting(true)
      setRouteError('')
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
  }, [destination, origin])

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    if (!routeQuote?.id) {
      setError('Najpierw wybierz poprawny punkt A i B oraz poczekaj na wyznaczenie trasy.')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      await createJob({
        title: form.title,
        description: form.description,
        price: Number(form.price),
        routeQuoteId: routeQuote.id,
      })
      navigate('/my-jobs')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się opublikować zlecenia.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="create-job-page">
      <header className="page-heading">
        <span className="eyebrow">Nowe zlecenie</span>
        <h1>Skąd i dokąd?</h1>
        <p>Wybierz punkt A i B jak w aplikacji transportowej. Publicznie pokazujemy tylko obszary; dokładne adresy dostanie przypisany wykonawca dopiero podczas aktywnego zlecenia.</p>
      </header>

      <form className="panel create-job-form" onSubmit={submit}>
        <div className="create-job-form__wide">
          <RouteMapPicker
            origin={origin}
            destination={destination}
            routeQuote={routeQuote}
            onPointChange={updatePoint}
            disabled={submitting}
          />
        </div>

        <div className="create-job-form__wide route-summary" aria-live="polite">
          {routing && <span>Wyznaczanie trasy i czasu dojazdu…</span>}
          {!routing && routeQuote && (
            <>
              <strong>{formatDistance(routeQuote.distanceMeters)} · około {formatDuration(routeQuote.durationSeconds)}</strong>
              <span>{routeQuote.origin.publicLabel} → {routeQuote.destination.publicLabel}</span>
              {routeQuote.provider === 'DETERMINISTIC_DEV' && <small>Tryb deweloperski — prawdziwe ETA Google pojawi się po konfiguracji Routes API.</small>}
            </>
          )}
          {routeError && <span className="form-message form-message--error">{routeError}</span>}
        </div>

        <label className="field create-job-form__wide">
          <span>Tytuł</span>
          <input name="title" value={form.title} onChange={updateField} minLength={3} maxLength={160} placeholder="np. Odbierz paczkę z punktu A i dowieź do B" required />
        </label>
        <label className="field create-job-form__wide">
          <span>Opis</span>
          <textarea name="description" value={form.description} onChange={updateField} minLength={10} maxLength={4000} rows={6} placeholder="Opisz co trzeba odebrać, komu przekazać i wszystkie ważne szczegóły." required />
        </label>
        <label className="field">
          <span>Wynagrodzenie</span>
          <input name="price" type="number" value={form.price} onChange={updateField} min="0.01" step="0.01" placeholder="25.00" required />
        </label>

        <div className="create-job-form__wide create-job-form__actions">
          <span className="create-job-form__privacy">Dokładne A/B są chronione. Publiczne ogłoszenie nie zawiera współrzędnych ani numerów adresów.</span>
          <button className="button button--primary" type="submit" disabled={submitting || routing || !routeQuote?.id}>
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
