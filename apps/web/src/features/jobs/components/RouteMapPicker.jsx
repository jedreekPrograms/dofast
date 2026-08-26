import { useCallback, useEffect, useRef, useState } from 'react'
import { hasGoogleMapsKey, loadGoogleMaps } from '../../../shared/maps/googleMapsLoader.js'
import { decodeGooglePolyline } from '../../../shared/maps/polyline.js'
import './RouteMapPicker.css'

const WROCLAW = { lat: 51.1079, lng: 17.0385 }
const MAX_STOPS = 10

function RouteMapPicker({
  origin,
  stops = [],
  destination,
  routeQuote,
  onPointChange,
  onAddStop,
  onRemoveStop,
  disabled = false,
}) {
  const stopCount = stops.length
  const mapElementRef = useRef(null)
  const originAutocompleteRef = useRef(null)
  const destinationAutocompleteRef = useRef(null)
  const stopAutocompleteRefs = useRef([])
  const mapRef = useRef(null)
  const originMarkerRef = useRef(null)
  const destinationMarkerRef = useRef(null)
  const stopMarkerRefs = useRef([])
  const routeLineRef = useRef(null)
  const geocoderRef = useRef(null)
  const activePointRef = useRef('origin')
  const disabledRef = useRef(disabled)
  const [activePoint, setActivePoint] = useState('origin')
  const [mapsStatus, setMapsStatus] = useState(hasGoogleMapsKey() ? 'loading' : 'unconfigured')
  const [mapError, setMapError] = useState('')

  useEffect(() => {
    activePointRef.current = activePoint
  }, [activePoint])

  useEffect(() => {
    disabledRef.current = disabled
  }, [disabled])

  const choosePoint = useCallback((kind, point) => {
    onPointChange(kind, point)
    const next = nextPointKind(kind, stopCount)
    activePointRef.current = next
    setActivePoint(next)
  }, [onPointChange, stopCount])

  useEffect(() => {
    if (!hasGoogleMapsKey()) return undefined

    let cancelled = false
    const listeners = []
    const autocompleteElements = []

    async function initialize() {
      try {
        await loadGoogleMaps()
        const [{ Map }, { AdvancedMarkerElement, PinElement }, { PlaceAutocompleteElement }, { Geocoder }] = await Promise.all([
          window.google.maps.importLibrary('maps'),
          window.google.maps.importLibrary('marker'),
          window.google.maps.importLibrary('places'),
          window.google.maps.importLibrary('geocoding'),
        ])
        if (cancelled) return

        const map = new Map(mapElementRef.current, {
          center: WROCLAW,
          zoom: 12,
          mapId: import.meta.env.VITE_GOOGLE_MAPS_MAP_ID || 'DEMO_MAP_ID',
          streetViewControl: false,
          mapTypeControl: false,
          fullscreenControl: false,
        })
        mapRef.current = map
        geocoderRef.current = new Geocoder()

        originMarkerRef.current = new AdvancedMarkerElement({
          map,
          content: new PinElement({ glyphText: 'A', scale: 1.15 }),
          title: 'Punkt A',
        })
        stopMarkerRefs.current = Array.from({ length: stopCount }, (_, index) => new AdvancedMarkerElement({
          map,
          content: new PinElement({ glyphText: String(index + 1), scale: 1.05 }),
          title: `Przystanek ${index + 1}`,
        }))
        destinationMarkerRef.current = new AdvancedMarkerElement({
          map,
          content: new PinElement({ glyphText: 'B', scale: 1.15 }),
          title: 'Punkt B',
        })

        function mountAutocomplete(container, kind, placeholder) {
          if (!container) return
          const autocomplete = new PlaceAutocompleteElement()
          autocomplete.placeholder = placeholder
          autocomplete.setAttribute('aria-label', placeholder)
          container.replaceChildren(autocomplete)
          autocompleteElements.push(autocomplete)

          const handler = async ({ placePrediction }) => {
            try {
              const place = placePrediction.toPlace()
              await place.fetchFields({
                fields: ['id', 'displayName', 'formattedAddress', 'location', 'viewport', 'addressComponents'],
              })
              if (!place.location) return
              choosePoint(kind, {
                latitude: place.location.lat(),
                longitude: place.location.lng(),
                publicLabel: publicAreaLabel(place.addressComponents),
                privateLabel: place.formattedAddress || place.displayName || 'Wybrany punkt',
                placeId: place.id || null,
              })
              if (place.viewport) map.fitBounds(place.viewport)
              else {
                map.panTo(place.location)
                map.setZoom(16)
              }
            } catch {
              setMapError('Nie udało się pobrać szczegółów wybranego adresu.')
            }
          }
          autocomplete.addEventListener('gmp-select', handler)
          listeners.push(() => autocomplete.removeEventListener('gmp-select', handler))
        }

        mountAutocomplete(originAutocompleteRef.current, 'origin', 'Punkt A — skąd zacząć?')
        for (let index = 0; index < stopCount; index += 1) {
          mountAutocomplete(stopAutocompleteRefs.current[index], `stop-${index}`, `Przystanek ${index + 1} — gdzie po drodze?`)
        }
        mountAutocomplete(destinationAutocompleteRef.current, 'destination', 'Punkt B — gdzie zakończyć?')

        const mapClickListener = map.addListener('click', async (event) => {
          if (disabledRef.current || !event.latLng) return
          const coordinates = { lat: event.latLng.lat(), lng: event.latLng.lng() }
          const kind = activePointRef.current
          try {
            const response = await geocoderRef.current.geocode({ location: coordinates })
            const result = response.results?.[0]
            choosePoint(kind, {
              latitude: coordinates.lat,
              longitude: coordinates.lng,
              publicLabel: result ? publicAreaLabel(result.address_components) : 'Wybrany obszar',
              privateLabel: result?.formatted_address || formatCoordinates(coordinates),
              placeId: result?.place_id || null,
            })
          } catch {
            choosePoint(kind, {
              latitude: coordinates.lat,
              longitude: coordinates.lng,
              publicLabel: 'Wybrany obszar',
              privateLabel: formatCoordinates(coordinates),
              placeId: null,
            })
          }
        })
        listeners.push(() => mapClickListener.remove())
        setMapsStatus('ready')
      } catch (error) {
        setMapsStatus('error')
        setMapError(error.message || 'Nie udało się uruchomić mapy.')
      }
    }

    initialize()
    return () => {
      cancelled = true
      listeners.forEach((cleanup) => cleanup())
      autocompleteElements.forEach((element) => element.remove())
      if (routeLineRef.current) routeLineRef.current.setMap(null)
      if (originMarkerRef.current) originMarkerRef.current.map = null
      stopMarkerRefs.current.forEach((marker) => { marker.map = null })
      if (destinationMarkerRef.current) destinationMarkerRef.current.map = null
      stopMarkerRefs.current = []
      mapRef.current = null
    }
  }, [choosePoint, stopCount])

  useEffect(() => {
    const map = mapRef.current
    if (!map || mapsStatus !== 'ready') return

    originMarkerRef.current.position = origin ? toLatLng(origin) : null
    stopMarkerRefs.current.forEach((marker, index) => {
      marker.position = stops[index] ? toLatLng(stops[index]) : null
    })
    destinationMarkerRef.current.position = destination ? toLatLng(destination) : null

    if (routeLineRef.current) {
      routeLineRef.current.setMap(null)
      routeLineRef.current = null
    }

    if (!origin || !destination) return

    const decodedPath = decodeGooglePolyline(routeQuote?.encodedPolyline)
    const fallbackPath = [origin, ...stops.filter(Boolean), destination].map(toLatLng)
    const path = decodedPath.length >= 2 ? decodedPath : fallbackPath
    routeLineRef.current = new window.google.maps.Polyline({
      path,
      map,
      geodesic: false,
      strokeOpacity: 0.85,
      strokeWeight: 5,
    })

    const bounds = new window.google.maps.LatLngBounds()
    path.forEach((point) => bounds.extend(point))
    map.fitBounds(bounds, 56)
  }, [destination, mapsStatus, origin, routeQuote, stops])

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      setMapError('Ta przeglądarka nie udostępnia geolokalizacji.')
      return
    }
    setMapError('')
    navigator.geolocation.getCurrentPosition(async (position) => {
      const coordinates = { lat: position.coords.latitude, lng: position.coords.longitude }
      let privateLabel = formatCoordinates(coordinates)
      let publicLabel = 'Twoja okolica'
      let placeId = null
      try {
        if (geocoderRef.current) {
          const response = await geocoderRef.current.geocode({ location: coordinates })
          const result = response.results?.[0]
          if (result) {
            privateLabel = result.formatted_address || privateLabel
            publicLabel = publicAreaLabel(result.address_components)
            placeId = result.place_id || null
          }
        }
      } catch {
        // Coordinates remain usable even if reverse geocoding is temporarily unavailable.
      }
      choosePoint('origin', {
        latitude: coordinates.lat,
        longitude: coordinates.lng,
        publicLabel,
        privateLabel,
        placeId,
      })
      mapRef.current?.panTo(coordinates)
      mapRef.current?.setZoom(16)
    }, () => setMapError('Nie udało się pobrać bieżącej lokalizacji.'), {
      enableHighAccuracy: true,
      timeout: 10000,
    })
  }

  function removeStop(index, event) {
    event.stopPropagation()
    const activeStopIndex = stopIndex(activePointRef.current)
    if (activeStopIndex !== null && activeStopIndex >= index) {
      activePointRef.current = 'destination'
      setActivePoint('destination')
    }
    onRemoveStop?.(index)
  }

  if (mapsStatus === 'unconfigured') {
    return (
      <ManualRoutePicker
        origin={origin}
        stops={stops}
        destination={destination}
        onPointChange={onPointChange}
        onAddStop={onAddStop}
        onRemoveStop={onRemoveStop}
      />
    )
  }

  return (
    <section className="route-picker">
      <div className="route-picker__inputs">
        <PointRow
          kind="origin"
          badge="A"
          activePoint={activePoint}
          onActivate={setActivePoint}
          autocompleteRef={originAutocompleteRef}
          point={origin}
        />

        {stops.map((stop, index) => (
          <div
            className={`route-picker__point ${activePoint === `stop-${index}` ? 'route-picker__point--active' : ''}`}
            key={`stop-${index}`}
            onClick={() => setActivePoint(`stop-${index}`)}
          >
            <span className="route-picker__badge">{index + 1}</span>
            <div
              ref={(element) => { stopAutocompleteRefs.current[index] = element }}
              className="route-picker__autocomplete"
            />
            <button
              type="button"
              className="route-picker__remove-stop"
              disabled={disabled}
              onClick={(event) => removeStop(index, event)}
              aria-label={`Usuń przystanek ${index + 1}`}
            >
              Usuń
            </button>
            {stop && <small>{stop.privateLabel}</small>}
          </div>
        ))}

        {stopCount < MAX_STOPS && (
          <button
            type="button"
            className="button button--secondary route-picker__add-stop"
            onClick={onAddStop}
            disabled={disabled}
          >
            + Dodaj przystanek {stopCount > 0 ? `(${stopCount}/${MAX_STOPS})` : ''}
          </button>
        )}

        <PointRow
          kind="destination"
          badge="B"
          activePoint={activePoint}
          onActivate={setActivePoint}
          autocompleteRef={destinationAutocompleteRef}
          point={destination}
        />
      </div>

      <div className="route-picker__toolbar">
        <span>Kliknij A, przystanek lub B, a potem wskaż punkt na mapie.</span>
        <button type="button" className="button button--secondary" onClick={useCurrentLocation} disabled={disabled || mapsStatus !== 'ready'}>
          Użyj mojej lokalizacji jako A
        </button>
      </div>

      <div ref={mapElementRef} className="route-picker__map" aria-label="Mapa wyboru trasy" />
      {mapsStatus === 'loading' && <div className="route-picker__status">Ładowanie mapy Google…</div>}
      {mapError && <div className="form-message form-message--error">{mapError}</div>}
    </section>
  )
}

function PointRow({ kind, badge, activePoint, onActivate, autocompleteRef, point }) {
  return (
    <div
      className={`route-picker__point ${activePoint === kind ? 'route-picker__point--active' : ''}`}
      onClick={() => onActivate(kind)}
    >
      <span className="route-picker__badge">{badge}</span>
      <div ref={autocompleteRef} className="route-picker__autocomplete" />
      {point && <small>{point.privateLabel}</small>}
    </div>
  )
}

function ManualRoutePicker({ origin, stops, destination, onPointChange, onAddStop, onRemoveStop }) {
  return (
    <section className="route-picker route-picker--manual">
      <div className="route-picker__dev-note">
        <strong>Tryb lokalny bez Google Maps</strong>
        <span>Dodaj VITE_GOOGLE_MAPS_BROWSER_KEY, aby dostać mapę, autocomplete i wybór punktów kliknięciem.</span>
      </div>
      <ManualPoint label="Punkt A" value={origin} onChange={(point) => onPointChange('origin', point)} />
      {stops.map((stop, index) => (
        <div className="route-picker__manual-stop" key={`manual-stop-${index}`}>
          <ManualPoint label={`Przystanek ${index + 1}`} value={stop} onChange={(point) => onPointChange(`stop-${index}`, point)} />
          <button type="button" className="button button--secondary" onClick={() => onRemoveStop?.(index)}>Usuń przystanek</button>
        </div>
      ))}
      {stops.length < MAX_STOPS && <button type="button" className="button button--secondary" onClick={onAddStop}>+ Dodaj przystanek</button>}
      <ManualPoint label="Punkt B" value={destination} onChange={(point) => onPointChange('destination', point)} />
    </section>
  )
}

function ManualPoint({ label, value, onChange }) {
  const current = value || { latitude: '', longitude: '', publicLabel: '', privateLabel: '', placeId: null }

  function change(field, rawValue) {
    const next = { ...current, [field]: rawValue }
    const latitude = Number(next.latitude)
    const longitude = Number(next.longitude)
    if (Number.isFinite(latitude) && Number.isFinite(longitude) && next.publicLabel?.trim() && next.privateLabel?.trim()) {
      onChange({ ...next, latitude, longitude, placeId: null })
    } else {
      onChange(next)
    }
  }

  return (
    <fieldset className="route-picker__manual-point">
      <legend>{label}</legend>
      <label className="field"><span>Dokładny adres</span><input value={current.privateLabel || ''} onChange={(event) => change('privateLabel', event.target.value)} /></label>
      <label className="field"><span>Publiczny obszar</span><input value={current.publicLabel || ''} onChange={(event) => change('publicLabel', event.target.value)} /></label>
      <label className="field"><span>Szerokość</span><input type="number" step="0.000001" value={current.latitude ?? ''} onChange={(event) => change('latitude', event.target.value)} /></label>
      <label className="field"><span>Długość</span><input type="number" step="0.000001" value={current.longitude ?? ''} onChange={(event) => change('longitude', event.target.value)} /></label>
    </fieldset>
  )
}

function nextPointKind(kind, stopCount) {
  if (kind === 'origin') return stopCount > 0 ? 'stop-0' : 'destination'
  const index = stopIndex(kind)
  if (index !== null) return index + 1 < stopCount ? `stop-${index + 1}` : 'destination'
  return 'origin'
}

function stopIndex(kind) {
  if (!kind?.startsWith('stop-')) return null
  const value = Number(kind.slice(5))
  return Number.isInteger(value) && value >= 0 ? value : null
}

function publicAreaLabel(components = []) {
  const read = (type) => {
    const component = components.find((item) => item.types?.includes(type))
    return component?.longText || component?.long_name || component?.shortText || component?.short_name || null
  }
  const locality = read('locality') || read('postal_town') || read('administrative_area_level_2')
  const area = read('sublocality_level_1') || read('neighborhood') || read('sublocality')
  const values = [locality, area].filter(Boolean).filter((value, index, array) => array.indexOf(value) === index)
  return values.length ? values.join(', ') : 'Wybrany obszar'
}

function toLatLng(point) {
  return { lat: Number(point.latitude), lng: Number(point.longitude) }
}

function formatCoordinates({ lat, lng }) {
  return `${lat.toFixed(6)}, ${lng.toFixed(6)}`
}

export default RouteMapPicker
