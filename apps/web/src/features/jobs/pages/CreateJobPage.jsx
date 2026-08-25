import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createJob } from '../api/jobsApi.js'
import './CreateJobPage.css'

const EMPTY_FORM = {
  title: '',
  description: '',
  price: '',
  publicLabel: '',
  privateLabel: '',
  latitude: '',
  longitude: '',
}

function CreateJobPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [locating, setLocating] = useState(false)
  const [error, setError] = useState('')

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  function useCurrentPosition() {
    if (!navigator.geolocation) {
      setError('Ta przeglądarka nie udostępnia lokalizacji.')
      return
    }

    setLocating(true)
    setError('')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setForm((current) => ({
          ...current,
          latitude: position.coords.latitude.toFixed(6),
          longitude: position.coords.longitude.toFixed(6),
        }))
        setLocating(false)
      },
      () => {
        setError('Nie udało się pobrać lokalizacji. Możesz wpisać współrzędne ręcznie.')
        setLocating(false)
      },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  async function submit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await createJob({
        title: form.title,
        description: form.description,
        price: Number(form.price),
        location: {
          publicLabel: form.publicLabel,
          privateLabel: form.privateLabel || null,
          latitude: Number(form.latitude),
          longitude: Number(form.longitude),
        },
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
        <h1>Powiedz, czego potrzebujesz</h1>
        <p>Publicznie pokażemy tylko przybliżony obszar. Dokładny adres jest chroniony i trafia wyłącznie do stron aktywnego zlecenia.</p>
      </header>

      <form className="panel create-job-form" onSubmit={submit}>
        <label className="field create-job-form__wide">
          <span>Tytuł</span>
          <input name="title" value={form.title} onChange={updateField} minLength={3} maxLength={160} placeholder="np. Odbierz paczkę i przywieź na Nadodrze" required />
        </label>
        <label className="field create-job-form__wide">
          <span>Opis</span>
          <textarea name="description" value={form.description} onChange={updateField} minLength={10} maxLength={4000} rows={6} placeholder="Opisz dokładnie zadanie, termin i ważne szczegóły." required />
        </label>
        <label className="field">
          <span>Wynagrodzenie</span>
          <input name="price" type="number" value={form.price} onChange={updateField} min="0.01" step="0.01" placeholder="25.00" required />
        </label>
        <label className="field">
          <span>Publiczny obszar</span>
          <input name="publicLabel" value={form.publicLabel} onChange={updateField} maxLength={160} placeholder="Wrocław, Plac Grunwaldzki" required />
        </label>
        <label className="field create-job-form__wide">
          <span>Dokładny adres / instrukcja</span>
          <input name="privateLabel" value={form.privateLabel} onChange={updateField} maxLength={300} placeholder="ul. ..., klatka ..., domofon ..." />
          <small>To pole nie pojawi się w publicznej liście.</small>
        </label>
        <label className="field">
          <span>Szerokość geograficzna</span>
          <input name="latitude" type="number" value={form.latitude} onChange={updateField} min="-90" max="90" step="0.000001" required />
        </label>
        <label className="field">
          <span>Długość geograficzna</span>
          <input name="longitude" type="number" value={form.longitude} onChange={updateField} min="-180" max="180" step="0.000001" required />
        </label>
        <div className="create-job-form__wide create-job-form__actions">
          <button className="button button--secondary" type="button" onClick={useCurrentPosition} disabled={locating}>{locating ? 'Pobieranie…' : 'Użyj mojej lokalizacji'}</button>
          <button className="button button--primary" type="submit" disabled={submitting}>{submitting ? 'Publikowanie…' : 'Opublikuj zlecenie'}</button>
        </div>
        {error && <div className="form-message form-message--error create-job-form__wide">{error}</div>}
      </form>
    </main>
  )
}

export default CreateJobPage
