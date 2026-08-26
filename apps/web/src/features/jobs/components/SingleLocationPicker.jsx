import { useCallback, useEffect, useRef, useState } from 'react'
import { hasGoogleMapsKey, loadGoogleMaps } from '../../../shared/maps/googleMapsLoader.js'
import './RouteMapPicker.css'

const WROCLAW = { lat: 51.1079, lng: 17.0385 }

function SingleLocationPicker({ value, onChange, disabled = false }) {
  const mapElementRef = useRef(null)
  const autocompleteRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)
  const geocoderRef = useRef(null)
  const disabledRef = useRef(disabled)
  const [mapsStatus, setMapsStatus] = useState(hasGoogleMapsKey() ? 'loading' : 'unconfigured')
  const [mapError, setMapError] = useState('')

  useEffect(() => {
    disabledRef.current = disabled
  }, [disabled])

  const choosePoint = useCallback((point) => {
    onChange(point)
  }, [onChange])

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
        const pin = new PinElement({ glyphText: '•', scale: 1.15 })
        markerRef.current = new AdvancedMarkerElement({ map, content: pin, title: 'Miejsce wykonania' })

        const autocomplete = new PlaceAutocompleteElement()
        autocomplete.placeholder = 'Gdzie ma zostać wykonana usługa?'
        autocomplete.setAttribute('aria-label', 'Miejsce wykonania usługi')
        autocompleteRef.current.replaceChildren(autocomplete)
        autocompleteElements.push(autocomplete)

        const autocompleteHandler = async ({ placePrediction }) => {
          try {
            const place = placePrediction.toPlace()
            await place.fetchFields({
              fields: ['id', 'displayName', 'formattedAddress', 'location', 'viewport', 'addressComponents'],
            })
            if (!place.location) return
            choosePoint({
              latitude: place.location.lat(),
              longitude: place.location.lng(),
              publicLabel: publicAreaLabel(place.addressComponents),
              privateLabel: place.formattedAddress || place.displayName || 'Wybrane miejsce',
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
        autocomplete.addEventListener('gmp-select', autocompleteHandler)
        listeners.push(() => autocomplete.removeEventListener('gmp-select', autocompleteHandler))

        const mapClickListener = map.addListener('click', async (event) => {
          if (disabledRef.current || !event.latLng) return
          const coordinates = { lat: event.latLng.lat(), lng: event.latLng.lng() }
          try {
            const response = await geocoderRef.current.geocode({ location: coordinates })
            const result = response.results?.[0]
            choosePoint({
              latitude: coordinates.lat,
              longitude: coordinates.lng,
              publicLabel: result ? publicAreaLabel(result.address_components) : 'Wybrany obszar',
              privateLabel: result?.formatted_address || formatCoordinates(coordinates),
              placeId: result?.place_id || null,
            })
          } catch {
            choosePoint({
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
      if (markerRef.current) markerRef.current.map = null
      mapRef.current = null
    }
  }, [choosePoint])

  useEffect(() => {
    const map = mapRef.current
    if (!map || mapsStatus !== 'ready') return
    markerRef.current.position = value ? toLatLng(value) : null
    if (value) {
      map.panTo(toLatLng(value))
      if ((map.getZoom() || 0) < 15) map.setZoom(15)
    }
  }, [mapsStatus, value])

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
      choosePoint({ latitude: coordinates.lat, longitude: coordinates.lng, publicLabel, privateLabel, placeId })
      mapRef.current?.panTo(coordinates)
      mapRef.current?.setZoom(16)
    }, () => setMapError('Nie udało się pobrać bieżącej lokalizacji.'), {
      enableHighAccuracy: true,
      timeout: 10000,
    })
  }

  if (mapsStatus === 'unconfigured') {
    return <ManualLocationPicker value={value} onChange={onChange} />
  }

  return (
    <section className="route-picker">
      <div className="route-picker__inputs">
        <div className="route-picker__point route-picker__point--active">
          <span className="route-picker__badge">•</span>
          <div ref={autocompleteRef} className="route-picker__autocomplete" />
          {value && <small>{value.privateLabel}</small>}
        </div>
      </div>
      <div className="route-picker__toolbar">
        <span>Wyszukaj dokładny adres albo wskaż miejsce na mapie.</span>
        <button type="button" className="button button--secondary" onClick={useCurrentLocation} disabled={disabled || mapsStatus !== 'ready'}>
          Użyj mojej lokalizacji
        </button>
      </div>
      <div ref={mapElementRef} className="route-picker__map" aria-label="Mapa wyboru miejsca wykonania usługi" />
      {mapsStatus === 'loading' && <div className="route-picker__status">Ładowanie mapy Google…</div>}
      {mapError && <div className="form-message form-message--error">{mapError}</div>}
    </section>
  )
}

function ManualLocationPicker({ value, onChange }) {
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
    <section className="route-picker route-picker--manual">
      <div className="route-picker__dev-note">
        <strong>Tryb lokalny bez Google Maps</strong>
        <span>Dodaj VITE_GOOGLE_MAPS_BROWSER_KEY, aby dostać mapę, autocomplete i wybór punktu kliknięciem.</span>
      </div>
      <fieldset className="route-picker__manual-point">
        <legend>Miejsce wykonania</legend>
        <label className="field"><span>Dokładny adres</span><input value={current.privateLabel || ''} onChange={(event) => change('privateLabel', event.target.value)} /></label>
        <label className="field"><span>Publiczny obszar</span><input value={current.publicLabel || ''} onChange={(event) => change('publicLabel', event.target.value)} /></label>
        <label className="field"><span>Szerokość</span><input type="number" step="0.000001" value={current.latitude ?? ''} onChange={(event) => change('latitude', event.target.value)} /></label>
        <label className="field"><span>Długość</span><input type="number" step="0.000001" value={current.longitude ?? ''} onChange={(event) => change('longitude', event.target.value)} /></label>
      </fieldset>
    </section>
  )
}

function publicAreaLabel(components = []) {
  const read = (type) => {
    const component = components.find((item) => item.types?.includes(type))
    return component?.longText || component?.long_name || component?.shortText || component?.short_name || null
  }
  const locality = read('locality') || read('postal_town') || read('administrative_area_level_2')
  const area = read('sublocality_level_1') || read('neighborhood') || read('sublocality')
  const values = [locality, area].filter(Boolean).filter((item, index, array) => array.indexOf(item) === index)
  return values.length ? values.join(', ') : 'Wybrany obszar'
}

function toLatLng(point) {
  return { lat: Number(point.latitude), lng: Number(point.longitude) }
}

function formatCoordinates({ lat, lng }) {
  return `${lat.toFixed(6)}, ${lng.toFixed(6)}`
}

export default SingleLocationPicker
