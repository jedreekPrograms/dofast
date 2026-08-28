import { useEffect, useState } from 'react'
import LocationMapPicker from '../../jobs/components/LocationMapPicker.jsx'
import {
  deleteMyServiceArea,
  getMyServiceArea,
  updateMyServiceArea,
} from '../api/profileApi.js'
import './ServiceAreaEditor.css'

const RADIUS_OPTIONS = [1, 2, 5, 10, 20, 30, 50, 100]

function ServiceAreaEditor() {
  const [location, setLocation] = useState(null)
  const [radiusKm, setRadiusKm] = useState('10')
  const [configured, setConfigured] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    getMyServiceArea()
      .then((response) => {
        if (!active) return
        if (!response?.configured) {
          setConfigured(false)
          return
        }

        setConfigured(true)
        setRadiusKm(String(response.radiusKm ?? 10))
        setLocation({
          latitude: response.latitude,
          longitude: response.longitude,
          publicLabel: 'Prywatny obszar działania',
          privateLabel: 'Zapisany środek obszaru działania',
          placeId: null,
        })
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać obszaru działania.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [])

  async function saveArea() {
    setMessage('')
    setError('')

    const latitude = Number(location?.latitude)
    const longitude = Number(location?.longitude)
    const radius = Number(radiusKm)

    if (!isValidCoordinate(latitude, longitude) || !Number.isInteger(radius) || radius < 1 || radius > 100) {
      setError('Wskaż prawidłowy punkt na mapie i wybierz promień od 1 do 100 km.')
      return
    }

    setSaving(true)
    try {
      const response = await updateMyServiceArea({
        latitude,
        longitude,
        radiusKm: radius,
      })
      setConfigured(true)
      setRadiusKm(String(response.radiusKm))
      setLocation((current) => ({
        ...(current || {}),
        latitude: response.latitude,
        longitude: response.longitude,
        publicLabel: current?.publicLabel || 'Prywatny obszar działania',
        privateLabel: current?.privateLabel || 'Zapisany środek obszaru działania',
        placeId: null,
      }))
      setMessage('Obszar działania został zapisany. Rekomendacje będą ograniczone do tego promienia.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zapisać obszaru działania.')
    } finally {
      setSaving(false)
    }
  }

  async function removeArea() {
    setMessage('')
    setError('')
    setSaving(true)
    try {
      await deleteMyServiceArea()
      setConfigured(false)
      setLocation(null)
      setRadiusKm('10')
      setMessage('Ograniczenie obszaru zostało wyłączone. Rekomendacje znów korzystają tylko ze specjalizacji.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wyłączyć obszaru działania.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel service-area-editor" aria-labelledby="service-area-title">
      <div className="service-area-editor__heading">
        <div>
          <span className="eyebrow">Prywatne dopasowanie</span>
          <h2 id="service-area-title">Obszar działania</h2>
          <p>
            Wskaż środek i promień, w którym chcesz otrzymywać rekomendacje zleceń zgodnych z Twoimi specjalizacjami.
          </p>
        </div>
        <span className={`service-area-editor__status ${configured ? 'service-area-editor__status--active' : ''}`}>
          {configured ? 'Aktywny' : 'Nieustawiony'}
        </span>
      </div>

      <div className="service-area-editor__privacy">
        <strong>Dokładny punkt jest prywatny.</strong>
        <span>
          Nie trafia do publicznego profilu, ofert, adresu URL ani do innych użytkowników. Backend używa go wyłącznie do przestrzennego filtrowania rekomendacji.
        </span>
      </div>

      {loading && <div className="page-state">Pobieranie obszaru działania…</div>}

      {!loading && (
        <>
          <LocationMapPicker
            location={location}
            onLocationChange={(nextLocation) => {
              setLocation(nextLocation)
              setMessage('')
              setError('')
            }}
            disabled={saving}
          />

          <div className="service-area-editor__controls">
            <label className="field">
              <span>Promień działania</span>
              <select value={radiusKm} onChange={(event) => setRadiusKm(event.target.value)} disabled={saving}>
                {RADIUS_OPTIONS.map((radius) => (
                  <option key={radius} value={radius}>{radius} km</option>
                ))}
              </select>
            </label>
            <div className="service-area-editor__actions">
              <button className="button button--primary" type="button" disabled={saving} onClick={saveArea}>
                {saving ? 'Zapisywanie…' : configured ? 'Zaktualizuj obszar' : 'Ustaw obszar'}
              </button>
              {configured && (
                <button className="button button--secondary" type="button" disabled={saving} onClick={removeArea}>
                  Wyłącz ograniczenie
                </button>
              )}
            </div>
          </div>
        </>
      )}

      {message && <div className="form-message">{message}</div>}
      {error && <div className="form-message form-message--error" role="alert">{error}</div>}
    </section>
  )
}

function isValidCoordinate(latitude, longitude) {
  return Number.isFinite(latitude)
    && latitude >= -90
    && latitude <= 90
    && Number.isFinite(longitude)
    && longitude >= -180
    && longitude <= 180
}

export default ServiceAreaEditor
