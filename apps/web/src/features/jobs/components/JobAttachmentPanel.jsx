import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  deleteJobAttachment,
  downloadJobAttachment,
  getJobAttachments,
  uploadJobAttachment,
} from '../api/jobsApi.js'
import './JobAttachmentPanel.css'

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024
const ACCEPTED_FILE_TYPES = 'image/jpeg,image/png,image/webp,application/pdf'

const VISIBILITY_OPTIONS = [
  {
    value: 'JOB_VIEWERS',
    label: 'Dla osób oglądających zlecenie',
    description: 'Np. lista zakupów albo zdjęcie produktu. Widoczne zalogowanym kandydatom tylko, gdy zlecenie jest otwarte; wybrany wykonawca zachowuje dostęp.',
  },
  {
    value: 'PARTICIPANTS',
    label: 'Tylko dla stron zlecenia',
    description: 'Materiał transakcyjny. Kandydaci go nie widzą; po wyborze dostęp dostaje przypisany wykonawca i zachowuje go jako historię zlecenia.',
  },
  {
    value: 'EXECUTION_SECRET',
    label: 'Tylko na czas realizacji',
    description: 'Np. kod odbioru lub karta lojalnościowa. Wybrany wykonawca ma dostęp wyłącznie w trakcie statusu „W realizacji”.',
  },
]

const VISIBILITY_LABELS = Object.fromEntries(
  VISIBILITY_OPTIONS.map((option) => [option.value, option.label]),
)

const dateFormatter = new Intl.DateTimeFormat('pl-PL', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function JobAttachmentPanel({ job, currentUserId, onSuccess }) {
  const isCreator = job.createdById === currentUserId
  const canUpload = isCreator && (job.status === 'OPEN' || job.status === 'IN_PROGRESS')
  const [attachments, setAttachments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyAction, setBusyAction] = useState('')
  const [visibility, setVisibility] = useState(defaultVisibility(job))
  const [file, setFile] = useState(null)
  const fileInputRef = useRef(null)

  const selectedVisibility = useMemo(
    () => VISIBILITY_OPTIONS.find((option) => option.value === visibility),
    [visibility],
  )

  const loadAttachments = useCallback(async (signal) => {
    if (!currentUserId) {
      setAttachments([])
      setLoading(false)
      return
    }

    setLoading(true)
    setError('')
    try {
      setAttachments(await getJobAttachments(job.id, { signal }))
    } catch (requestError) {
      if (requestError.name !== 'AbortError') {
        setError(requestError.message || 'Nie udało się pobrać załączników.')
      }
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [currentUserId, job.id])

  useEffect(() => {
    const controller = new AbortController()
    loadAttachments(controller.signal)
    return () => controller.abort()
  }, [job.status, job.takenById, loadAttachments])

  useEffect(() => {
    if (!canUpload) {
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }, [canUpload])

  function handleFileChange(event) {
    const selected = event.target.files?.[0] || null
    setError('')
    if (selected && selected.size > MAX_FILE_SIZE_BYTES) {
      setFile(null)
      event.target.value = ''
      setError('Plik jest większy niż 10 MB.')
      return
    }
    setFile(selected)
  }

  async function handleUpload(event) {
    event.preventDefault()
    if (!file) {
      setError('Wybierz plik do dodania.')
      return
    }

    setBusyAction('upload')
    setError('')
    try {
      const created = await uploadJobAttachment(job.id, visibility, file)
      setAttachments((current) => [...current, created])
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      onSuccess?.('Załącznik został bezpiecznie dodany do zlecenia.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się dodać załącznika.')
    } finally {
      setBusyAction('')
    }
  }

  async function handleDownload(attachment) {
    setBusyAction(`download-${attachment.id}`)
    setError('')
    try {
      const blob = await downloadJobAttachment(job.id, attachment.id)
      triggerPrivateDownload(blob, attachment.originalFilename)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać załącznika.')
    } finally {
      setBusyAction('')
    }
  }

  async function handleDelete(attachment) {
    setBusyAction(`delete-${attachment.id}`)
    setError('')
    try {
      await deleteJobAttachment(job.id, attachment.id)
      setAttachments((current) => current.filter((item) => item.id !== attachment.id))
      onSuccess?.('Załącznik został usunięty.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się usunąć załącznika.')
    } finally {
      setBusyAction('')
    }
  }

  if (!currentUserId) return null

  return (
    <section className="job-details-panel job-attachment-panel">
      <div className="job-attachment-panel__heading">
        <div>
          <span className="eyebrow">Materiały do zlecenia</span>
          <h2>Załączniki</h2>
        </div>
        <span className="job-attachment-panel__count">{attachments.length} / 12</span>
      </div>

      <p className="job-attachment-panel__intro">
        Dozwolone: JPG, PNG, WebP i PDF do 10 MB. Serwer sprawdza rzeczywisty format pliku i przechowuje zawartość zaszyfrowaną.
      </p>

      {error && <div className="form-message form-message--error" role="alert">{error}</div>}

      {canUpload && (
        <form className="job-attachment-upload" onSubmit={handleUpload}>
          <fieldset>
            <legend>Kto ma zobaczyć plik?</legend>
            <div className="job-attachment-visibility-grid">
              {VISIBILITY_OPTIONS.map((option) => (
                <label
                  className={`job-attachment-visibility ${visibility === option.value ? 'job-attachment-visibility--selected' : ''}`}
                  key={option.value}
                >
                  <input
                    type="radio"
                    name="attachment-visibility"
                    value={option.value}
                    checked={visibility === option.value}
                    onChange={(event) => setVisibility(event.target.value)}
                    disabled={Boolean(busyAction)}
                  />
                  <span>
                    <strong>{option.label}</strong>
                    <small>{option.description}</small>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          {visibility === 'EXECUTION_SECRET' && (
            <div className="job-attachment-secret-warning" role="note">
              <strong>Materiał tymczasowy, nie sejf na dane wrażliwe.</strong>
              <span>Używaj np. dla kodu odbioru lub karty lojalnościowej. Nie przesyłaj kart płatniczych, haseł, dokumentów tożsamości ani danych bankowych. Dostęp wykonawcy znika po wyjściu z „W realizacji”.</span>
            </div>
          )}

          <div className="job-attachment-upload__file-row">
            <label className="field">
              <span>Plik</span>
              <input
                ref={fileInputRef}
                type="file"
                accept={ACCEPTED_FILE_TYPES}
                onChange={handleFileChange}
                disabled={Boolean(busyAction) || attachments.length >= 12}
                required
              />
              <small>{file ? `${file.name} · ${formatBytes(file.size)}` : selectedVisibility?.description}</small>
            </label>
            <button
              className="button button--primary"
              type="submit"
              disabled={Boolean(busyAction) || !file || attachments.length >= 12}
            >
              {busyAction === 'upload' ? 'Szyfrowanie i wysyłanie…' : 'Dodaj załącznik'}
            </button>
          </div>
        </form>
      )}

      {isCreator && !canUpload && (
        <div className="job-attachment-panel__locked-note">
          Nowych plików nie można już dodawać na tym etapie zlecenia. Tajne materiały nadal możesz usunąć.
        </div>
      )}

      {loading && <div className="page-state">Pobieranie listy załączników…</div>}

      {!loading && attachments.length === 0 && (
        <div className="page-state">Brak dostępnych dla Ciebie załączników.</div>
      )}

      {!loading && attachments.length > 0 && (
        <div className="job-attachment-list">
          {attachments.map((attachment) => {
            const secret = attachment.visibility === 'EXECUTION_SECRET'
            const canDelete = isCreator && (secret || !job.takenById)
            return (
              <article className={`job-attachment-card ${secret ? 'job-attachment-card--secret' : ''}`} key={attachment.id}>
                <div className="job-attachment-card__icon" aria-hidden="true">
                  {attachment.mediaType === 'application/pdf' ? 'PDF' : 'IMG'}
                </div>
                <div className="job-attachment-card__body">
                  <strong>{attachment.originalFilename}</strong>
                  <div className="job-attachment-card__meta">
                    <span>{VISIBILITY_LABELS[attachment.visibility] || attachment.visibility}</span>
                    <span>{formatBytes(attachment.sizeBytes)}</span>
                    <span>{dateFormatter.format(new Date(attachment.createdAt))}</span>
                  </div>
                  {secret && (
                    <small className="job-attachment-card__secret-note">
                      Brak podglądu w aplikacji. Pobierz tylko wtedy, gdy materiał jest potrzebny do bieżącej realizacji.
                    </small>
                  )}
                </div>
                <div className="job-attachment-card__actions">
                  <button
                    className="button button--secondary"
                    type="button"
                    disabled={Boolean(busyAction)}
                    onClick={() => handleDownload(attachment)}
                  >
                    {busyAction === `download-${attachment.id}` ? 'Pobieranie…' : secret ? 'Pobierz bez podglądu' : 'Pobierz'}
                  </button>
                  {canDelete && (
                    <button
                      className="button button--danger"
                      type="button"
                      disabled={Boolean(busyAction)}
                      onClick={() => handleDelete(attachment)}
                    >
                      {busyAction === `delete-${attachment.id}` ? 'Usuwanie…' : 'Usuń'}
                    </button>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}

function defaultVisibility(job) {
  return job.takenById ? 'PARTICIPANTS' : 'JOB_VIEWERS'
}

function formatBytes(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 0) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

function triggerPrivateDownload(blob, filename) {
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = safeFilename(filename)
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

function safeFilename(value) {
  const normalized = String(value || 'zalacznik').replaceAll('\\', '/')
  return normalized.split('/').pop() || 'zalacznik'
}

export default JobAttachmentPanel
