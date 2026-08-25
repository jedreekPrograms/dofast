import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getJobRoute } from '../api/jobsApi.js'
import { hasGoogleMapsKey, loadGoogleMaps } from '../../../shared/maps/googleMapsLoader.js'
import { decodeGooglePolyline } from '../../../shared/maps/polyline.js'
import './JobRoutePage.css'

function JobRoutePage() {
  const { jobId } = useParams()
  const mapElementRef = useRef(null)
  const [route, setRoute] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [mapError, setMapError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        setRoute(await getJobRoute(jobId, { signal: controller.signal }))
      } catch (requestError) {
        if (requestError.name !== 'AbortError') setError(requestError.message || 'Nie udało się pobrać trasy.')
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    load()
    return () => controller.abort()
  }, [jobId])

  useEffect(() => {
    if (!route || !hasGoogleMapsKey()) return undefined
    let cancelled = false
    let routeLine = null
    const markers = []

    async function renderMap() {
      try {
        await loadGoogleMaps()
        const [{ Map }, { AdvancedMarkerElement, PinElement }] = await Promise.all([
          window.google.maps.importLibrary('maps'),
          window.google.maps.importLibrary('marker'),
        ])
        if (cancelled) return

        const map = new Map(mapElementRef.current, {
          center: point(route.origin),
          zoom: 13,
          mapId: import.meta.env.VITE_GOOGLE_MAPS_MAP_ID || 'DEMO_MAP_ID',
          streetViewControl: false,
          mapTypeControl: false,
        })
        const originMarker = new AdvancedMarkerElement({ map, position: point(route.origin), content: new PinElement({ glyphText: 'A' }), title: route.origin.label })
        const destinationMarker = new AdvancedMarkerElement({ map, position: point(route.destination), content: new PinElement({ glyphText: 'B' }), title: route.destination.label })
        markers.push(originMarker, destinationMarker)

        const decoded = decodeGooglePolyline(route.encodedPolyline)
        const path = decoded.length >= 2 ? decoded : [point(route.origin), point(route.destination)]
        routeLine = new window.google.maps.Polyline({ path, map, strokeOpacity: 0.9, strokeWeight: 5 })
        const bounds = new window.google.maps.LatLngBounds()
        path.forEach((item) => bounds.extend(item))
        map.fitBounds(bounds, 64)
      } catch (requestError) {
        setMapError(requestError.message || 'Nie udało się wyświetlić mapy.')
      }
    }

    renderMap()
    return () => {
      cancelled = true
      if (routeLine) routeLine.setMap(null)
      markers.forEach((marker) => { marker.map = null })
    }
  }, [route])

  return (
    <main className="job-route-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Chroniona trasa</span>
          <h1>Trasa zlecenia #{jobId}</h1>
          <p>Dokładne punkty są widoczne wyłącznie dla uprawnionych stron zlecenia.</p>
        </div>
        <Link className="button button--secondary" to="/my-jobs">Wróć do zleceń</Link>
      </header>

      {loading && <div className="page-state">Pobieranie trasy…</div>}
      {error && <div className="form-message form-message--error">{error}</div>}

      {!loading && route && (
        <>
          <section className="panel job-route-summary">
            <div><span className="job-route-summary__badge">A</span><div><small>Punkt odbioru</small><strong>{route.origin.label}</strong></div></div>
            <div><span className="job-route-summary__badge">B</span><div><small>Punkt docelowy</small><strong>{route.destination.label}</strong></div></div>
            <div className="job-route-summary__metrics">
              <strong>{formatDistance(route.distanceMeters)}</strong>
              <span>około {formatDuration(route.durationSeconds)}</span>
            </div>
          </section>

          {hasGoogleMapsKey() ? <div ref={mapElementRef} className="job-route-map" aria-label="Dokładna mapa trasy" /> : (
            <div className="panel job-route-map-fallback">Mapa pojawi się po skonfigurowaniu Google Maps browser key. Dokładne A/B oraz parametry trasy powyżej pochodzą z backendu.</div>
          )}
          {mapError && <div className="form-message form-message--error">{mapError}</div>}
        </>
      )}
    </main>
  )
}

function point(value) {
  return { lat: Number(value.latitude), lng: Number(value.longitude) }
}

function formatDistance(meters) {
  return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(meters < 10_000 ? 1 : 0)} km`
}

function formatDuration(seconds) {
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  return remainder ? `${hours} godz. ${remainder} min` : `${hours} godz.`
}

export default JobRoutePage
