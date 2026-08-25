import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import { useRealtime } from '../../../shared/realtime/RealtimeContext.js'
import {
  confirmJobPickup,
  getJob,
  getJobRoute,
  getLiveTracking,
  updateLiveTracking,
} from '../api/jobsApi.js'
import { hasGoogleMapsKey, loadGoogleMaps } from '../../../shared/maps/googleMapsLoader.js'
import { decodeGooglePolyline } from '../../../shared/maps/polyline.js'
import './JobRoutePage.css'

const ACTIVE_TRACKING_STATUSES = new Set(['IN_PROGRESS', 'COMPLETION_REQUESTED'])
const GPS_SEND_INTERVAL_MS = 5000

function JobRoutePage() {
  const { jobId } = useParams()
  const { user } = useAuth()
  const { status: realtimeStatus, subscribe } = useRealtime()
  const mapElementRef = useRef(null)
  const mapRef = useRef(null)
  const courierMarkerRef = useRef(null)
  const remainingLineRef = useRef(null)
  const firstCourierPositionRef = useRef(true)
  const lastSentAtRef = useRef(0)
  const sendingPositionRef = useRef(false)

  const [route, setRoute] = useState(null)
  const [job, setJob] = useState(null)
  const [tracking, setTracking] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [mapError, setMapError] = useState('')
  const [trackingError, setTrackingError] = useState('')
  const [locationPermission, setLocationPermission] = useState('idle')
  const [pickupSubmitting, setPickupSubmitting] = useState(false)

  const trackingActive = job ? ACTIVE_TRACKING_STATUSES.has(job.status) : false
  const isWorker = Boolean(user && job && job.takenById === user.id)

  const refreshJob = useCallback(async (signal) => {
    const data = await getJob(jobId, signal ? { signal } : {})
    setJob(data)
    return data
  }, [jobId])

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      setError('')
      try {
        const [routeData, jobData] = await Promise.all([
          getJobRoute(jobId, { signal: controller.signal }),
          getJob(jobId, { signal: controller.signal }),
        ])
        setRoute(routeData)
        setJob(jobData)
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
    const interval = window.setInterval(() => {
      refreshJob().catch(() => {})
    }, 10000)
    return () => window.clearInterval(interval)
  }, [refreshJob])

  useEffect(() => {
    if (!trackingActive) {
      setTracking(null)
      return undefined
    }

    let mounted = true
    async function refreshTracking() {
      try {
        const data = await getLiveTracking(jobId)
        if (mounted) setTracking(data)
      } catch (requestError) {
        if (mounted && [403, 409, 404].includes(requestError.status)) setTracking(null)
      }
    }

    refreshTracking()
    const unsubscribe = subscribe(`/topic/tracking/${jobId}`, (message) => {
      if (mounted) setTracking(message)
    })
    const interval = window.setInterval(refreshTracking, 10000)
    return () => {
      mounted = false
      unsubscribe()
      window.clearInterval(interval)
    }
  }, [jobId, subscribe, trackingActive])

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
        mapRef.current = map
        markers.push(
          new AdvancedMarkerElement({ map, position: point(route.origin), content: new PinElement({ glyphText: 'A' }), title: route.origin.label }),
          new AdvancedMarkerElement({ map, position: point(route.destination), content: new PinElement({ glyphText: 'B' }), title: route.destination.label }),
        )

        const decoded = decodeGooglePolyline(route.encodedPolyline)
        const path = decoded.length >= 2 ? decoded : [point(route.origin), point(route.destination)]
        routeLine = new window.google.maps.Polyline({ path, map, strokeOpacity: 0.35, strokeWeight: 5 })
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
      mapRef.current = null
      if (routeLine) routeLine.setMap(null)
      if (remainingLineRef.current) remainingLineRef.current.setMap(null)
      if (courierMarkerRef.current) courierMarkerRef.current.map = null
      remainingLineRef.current = null
      courierMarkerRef.current = null
      firstCourierPositionRef.current = true
      markers.forEach((marker) => { marker.map = null })
    }
  }, [route])

  useEffect(() => {
    if (!mapRef.current || !route || !hasGoogleMapsKey()) return undefined
    let cancelled = false

    async function renderCourier() {
      if (!tracking?.sharingActive || !tracking.location) {
        if (courierMarkerRef.current) courierMarkerRef.current.map = null
        if (remainingLineRef.current) remainingLineRef.current.setMap(null)
        courierMarkerRef.current = null
        remainingLineRef.current = null
        firstCourierPositionRef.current = true
        return
      }

      const { AdvancedMarkerElement, PinElement } = await window.google.maps.importLibrary('marker')
      if (cancelled || !mapRef.current) return
      const courierPoint = point(tracking.location)
      const targetPoint = tracking.phase === 'TO_ORIGIN' ? point(route.origin) : point(route.destination)

      if (!courierMarkerRef.current) {
        courierMarkerRef.current = new AdvancedMarkerElement({
          map: mapRef.current,
          position: courierPoint,
          content: new PinElement({ glyphText: 'K' }),
          title: 'Aktualna pozycja wykonawcy',
        })
      } else {
        courierMarkerRef.current.position = courierPoint
      }

      if (remainingLineRef.current) remainingLineRef.current.setMap(null)
      const decoded = decodeGooglePolyline(tracking.remainingEncodedPolyline)
      const path = decoded.length >= 2 ? decoded : [courierPoint, targetPoint]
      remainingLineRef.current = new window.google.maps.Polyline({
        path,
        map: mapRef.current,
        strokeOpacity: 0.95,
        strokeWeight: 6,
      })

      if (firstCourierPositionRef.current) {
        const bounds = new window.google.maps.LatLngBounds()
        bounds.extend(courierPoint)
        bounds.extend(targetPoint)
        mapRef.current.fitBounds(bounds, 80)
        firstCourierPositionRef.current = false
      }
    }

    renderCourier().catch((requestError) => setMapError(requestError.message || 'Nie udało się odświeżyć pozycji kuriera.'))
    return () => { cancelled = true }
  }, [route, tracking])

  useEffect(() => {
    if (!isWorker || !trackingActive) {
      setLocationPermission('idle')
      return undefined
    }
    if (!navigator.geolocation) {
      setLocationPermission('unsupported')
      setTrackingError('Ta przeglądarka nie obsługuje udostępniania lokalizacji.')
      return undefined
    }

    let stopped = false
    setLocationPermission('requesting')
    setTrackingError('')

    const watchId = navigator.geolocation.watchPosition(
      async (position) => {
        if (stopped) return
        setLocationPermission('granted')
        const now = Date.now()
        if (sendingPositionRef.current || now - lastSentAtRef.current < GPS_SEND_INTERVAL_MS) return

        sendingPositionRef.current = true
        lastSentAtRef.current = now
        try {
          const response = await updateLiveTracking(jobId, {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracyMeters: finiteOrNull(position.coords.accuracy),
            headingDegrees: finiteOrNull(position.coords.heading),
            speedMetersPerSecond: finiteOrNull(position.coords.speed),
            capturedAt: new Date(position.timestamp).toISOString(),
          })
          if (!stopped) {
            setTracking(response)
            setTrackingError('')
          }
        } catch (requestError) {
          if (!stopped) setTrackingError(requestError.message || 'Nie udało się wysłać aktualnej lokalizacji.')
        } finally {
          sendingPositionRef.current = false
        }
      },
      (geoError) => {
        if (stopped) return
        setLocationPermission(geoError.code === 1 ? 'denied' : 'error')
        setTrackingError(geoError.code === 1
          ? 'Aby zlecający widział przejazd na żywo, zezwól na dostęp do lokalizacji.'
          : 'Nie udało się pobrać aktualnej lokalizacji urządzenia.')
      },
      { enableHighAccuracy: true, maximumAge: 3000, timeout: 15000 },
    )

    return () => {
      stopped = true
      navigator.geolocation.clearWatch(watchId)
      sendingPositionRef.current = false
    }
  }, [isWorker, jobId, trackingActive])

  async function handlePickup() {
    setPickupSubmitting(true)
    setTrackingError('')
    try {
      setTracking(await confirmJobPickup(jobId))
    } catch (requestError) {
      setTrackingError(requestError.message || 'Nie udało się potwierdzić odbioru.')
    } finally {
      setPickupSubmitting(false)
    }
  }

  const phaseLabel = tracking?.phase === 'TO_DESTINATION'
    ? 'Kurier jedzie do punktu B'
    : 'Kurier jedzie do punktu A'

  return (
    <main className="job-route-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Trasa i tracking live</span>
          <h1>Zlecenie #{jobId}</h1>
          <p>Dokładna trasa i bieżąca pozycja wykonawcy są dostępne wyłącznie dla stron aktywnego zlecenia.</p>
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

          {trackingActive && (
            <section className="panel live-tracking-card">
              <div className="live-tracking-card__heading">
                <div>
                  <span className="eyebrow">Na żywo</span>
                  <h2>{phaseLabel}</h2>
                </div>
                <span className={`realtime-status realtime-status--${realtimeStatus}`}>Realtime: {realtimeStatus}</span>
              </div>

              <div className="live-tracking-metrics">
                <div><small>Pozostało</small><strong>{tracking?.remainingDistanceMeters ? formatDistance(tracking.remainingDistanceMeters) : '—'}</strong></div>
                <div><small>ETA</small><strong>{tracking?.remainingDurationSeconds ? formatDuration(tracking.remainingDurationSeconds) : '—'}</strong></div>
                <div><small>Ostatni GPS</small><strong>{tracking?.receivedAt ? formatLastUpdate(tracking.receivedAt) : 'oczekiwanie'}</strong></div>
              </div>

              {!tracking?.sharingActive && <p className="live-tracking-note">Czekamy na pierwszą aktualizację GPS wykonawcy.</p>}
              {tracking?.stale && <div className="form-message form-message--error">Pozycja wykonawcy jest nieaktualna. Ostatnia aktualizacja mogła zostać wstrzymana przez telefon lub sieć.</div>}

              {isWorker && tracking?.phase !== 'TO_DESTINATION' && (
                <button className="button button--primary" type="button" disabled={pickupSubmitting} onClick={handlePickup}>
                  {pickupSubmitting ? 'Zapisywanie…' : 'Potwierdź odbiór w punkcie A'}
                </button>
              )}

              {isWorker && (
                <div className="live-tracking-worker-status">
                  <strong>{locationPermissionLabel(locationPermission)}</strong>
                  <span>Podczas realizacji nie zamykaj tego ekranu. W docelowej aplikacji mobilnej tracking będzie działał również w tle.</span>
                </div>
              )}
              {trackingError && <div className="form-message form-message--error">{trackingError}</div>}
            </section>
          )}

          {hasGoogleMapsKey() ? <div ref={mapElementRef} className="job-route-map" aria-label="Dokładna mapa trasy i lokalizacja kuriera" /> : (
            <div className="panel job-route-map-fallback">Mapa pojawi się po skonfigurowaniu Google Maps browser key. Dokładne A/B, bieżący GPS i ETA nadal pochodzą z backendu.</div>
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

function finiteOrNull(value) {
  return Number.isFinite(value) ? value : null
}

function formatDistance(meters) {
  if (!meters) return '—'
  return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(meters < 10_000 ? 1 : 0)} km`
}

function formatDuration(seconds) {
  if (!seconds) return '—'
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  return remainder ? `${hours} godz. ${remainder} min` : `${hours} godz.`
}

function formatLastUpdate(value) {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 1000))
  if (seconds < 10) return 'teraz'
  if (seconds < 60) return `${seconds} s temu`
  return `${Math.round(seconds / 60)} min temu`
}

function locationPermissionLabel(status) {
  if (status === 'granted') return 'Lokalizacja jest udostępniana'
  if (status === 'requesting') return 'Czekam na zgodę na lokalizację…'
  if (status === 'denied') return 'Dostęp do lokalizacji zablokowany'
  if (status === 'unsupported') return 'Brak obsługi geolokalizacji'
  if (status === 'error') return 'Problem z geolokalizacją'
  return 'Tracking GPS nieaktywny'
}

export default JobRoutePage
