import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getJob, getJobLocation } from '../api/jobsApi.js'
import { hasGoogleMapsKey, loadGoogleMaps } from '../../../shared/maps/googleMapsLoader.js'
import './JobLocationPage.css'

function JobLocationPage() {
  const { jobId } = useParams()
  const mapElementRef = useRef(null)
  const [job, setJob] = useState(null)
  const [location, setLocation] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [mapError, setMapError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      setError('')
      try {
        const [jobData, locationData] = await Promise.all([
          getJob(jobId, { signal: controller.signal }),
          getJobLocation(jobId, { signal: controller.signal }),
        ])
        if (jobData.fulfillmentMode !== 'ON_SITE') {
          throw new Error('To zlecenie korzysta z trasy A → B, a nie pojedynczego miejsca realizacji.')
        }
        setJob(jobData)
        setLocation(locationData)
      } catch (requestError) {
        if (requestError.name !== 'AbortError') {
          setError(requestError.message || 'Nie udało się pobrać miejsca realizacji.')
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    load()
    return () => controller.abort()
  }, [jobId])

  useEffect(() => {
    if (!location || !hasGoogleMapsKey()) return undefined
    let cancelled = false
    let marker = null

    async function renderMap() {
      try {
        await loadGoogleMaps()
        const [{ Map }, { AdvancedMarkerElement, PinElement }] = await Promise.all([
          window.google.maps.importLibrary('maps'),
          window.google.maps.importLibrary('marker'),
        ])
        if (cancelled) return

        const position = { lat: Number(location.latitude), lng: Number(location.longitude) }
        const map = new Map(mapElementRef.current, {
          center: position,
          zoom: 16,
          mapId: import.meta.env.VITE_GOOGLE_MAPS_MAP_ID || 'DEMO_MAP_ID',
          streetViewControl: false,
          mapTypeControl: false,
        })
        marker = new AdvancedMarkerElement({
          map,
          position,
          content: new PinElement({ glyphText: 'M', scale: 1.15 }),
          title: location.label,
        })
      } catch (requestError) {
        setMapError(requestError.message || 'Nie udało się wyświetlić mapy.')
      }
    }

    renderMap()
    return () => {
      cancelled = true
      if (marker) marker.map = null
    }
  }, [location])

  return (
    <main className="job-location-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Miejsce realizacji</span>
          <h1>Zlecenie #{jobId}</h1>
          <p>Dokładny adres jest chroniony i dostępny tylko zlecającemu oraz przypisanemu wykonawcy podczas aktywnego zlecenia.</p>
        </div>
        <Link className="button button--secondary" to={`/jobs/${jobId}`}>Wróć do szczegółów</Link>
      </header>

      {loading && <div className="page-state">Pobieranie dokładnego miejsca…</div>}
      {error && <div className="form-message form-message--error">{error}</div>}

      {!loading && job && location && (
        <>
          <section className="panel job-location-card">
            <div>
              <small>Publicznie widoczny obszar</small>
              <strong>{job.locationLabel || 'Lokalizacja'}</strong>
            </div>
            <div className="job-location-card__exact">
              <small>Dokładny adres realizacji</small>
              <strong>{location.label}</strong>
            </div>
          </section>

          {hasGoogleMapsKey() ? (
            <div ref={mapElementRef} className="job-location-map" aria-label="Dokładne miejsce wykonania usługi" />
          ) : (
            <div className="panel job-location-map-fallback">Mapa pojawi się po skonfigurowaniu Google Maps browser key. Dokładny adres powyżej nadal pochodzi z chronionego backendu.</div>
          )}
          {mapError && <div className="form-message form-message--error">{mapError}</div>}
        </>
      )}
    </main>
  )
}

export default JobLocationPage
