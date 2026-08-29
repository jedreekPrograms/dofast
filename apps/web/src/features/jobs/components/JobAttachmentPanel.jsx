import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  createJobExpenseClaim,
  deleteJobAttachment,
  downloadJobAttachment,
  getJobAttachments,
  getJobExpenseSummary,
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

const EXPENSE_STATUS_LABELS = {
  HELD: 'Budżet zabezpieczony',
  SETTLED: 'Budżet rozliczony',
  REFUNDED: 'Budżet zwrócony',
}

const moneyFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

const dateFormatter = new Intl.DateTimeFormat('pl-PL', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function JobAttachmentPanel({ job, currentUserId, onSuccess }) {
  const isCreator = job.createdById === currentUserId
  const isWorker = job.takenById === currentUserId
  const isParticipant = isCreator || isWorker
  const workerCanUpload = isWorker && job.status === 'IN_PROGRESS'
  const canUpload = (isCreator && (job.status === 'OPEN' || job.status === 'IN_PROGRESS')) || workerCanUpload
  const availableVisibilityOptions = workerCanUpload
    ? VISIBILITY_OPTIONS.filter((option) => option.value === 'PARTICIPANTS')
    : VISIBILITY_OPTIONS
  const [attachments, setAttachments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyAction, setBusyAction] = useState('')
  const [visibility, setVisibility] = useState(defaultVisibility(job))
  const [file, setFile] = useState(null)
  const [expenseSummary, setExpenseSummary] = useState(null)
  const [expenseLoading, setExpenseLoading] = useState(false)
  const [claimAmount, setClaimAmount] = useState('')
  const [claimAttachmentId, setClaimAttachmentId] = useState('')
  const fileInputRef = useRef(null)

  const selectedVisibility = useMemo(
    () => VISIBILITY_OPTIONS.find((option) => option.value === visibility),
    [visibility],
  )

  const claimedAttachmentIds = useMemo(
    () => new Set((expenseSummary?.claims || []).map((claim) => claim.attachmentId)),
    [expenseSummary?.claims],
  )

  const workerReceipts = useMemo(
    () => attachments.filter((attachment) => (
      attachment.uploadedById === currentUserId
      && attachment.visibility === 'PARTICIPANTS'
      && !claimedAttachmentIds.has(attachment.id)
      && isReceiptMediaType(attachment.mediaType)
    )),
    [attachments, claimedAttachmentIds, currentUserId],
  )

  const remainingExpenseBudget = useMemo(() => {
    if (!expenseSummary) return 0
    return Math.max(0, Number(expenseSummary.budgetAmount || 0) - Number(expenseSummary.claimedAmount || 0))
  }, [expenseSummary])

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

  const loadExpenseSummary = useCallback(async (signal) => {
    if (!isParticipant) {
      setExpenseSummary(null)
      return
    }

    setExpenseLoading(true)
    try {
      setExpenseSummary(await getJobExpenseSummary(job.id, { signal }))
    } catch (requestError) {
      if (requestError.name !== 'AbortError') {
        setError(requestError.message || 'Nie udało się pobrać rozliczenia kosztów.')
      }
    } finally {
      if (!signal?.aborted) setExpenseLoading(false)
    }
  }, [isParticipant, job.id])

  useEffect(() => {
    const controller = new AbortController()
    loadAttachments(controller.signal)
    return () => controller.abort()
  }, [job.status, job.takenById, loadAttachments])

  useEffect(() => {
    const controller = new AbortController()
    loadExpenseSummary(controller.signal)
    return () => controller.abort()
  }, [job.status, job.takenById, loadExpenseSummary])

  useEffect(() => {
    if (workerCanUpload) setVisibility('PARTICIPANTS')
    if (!canUpload) {
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }, [canUpload, workerCanUpload])

  useEffect(() => {
    if (claimAttachmentId && !workerReceipts.some((attachment) => String(attachment.id) === claimAttachmentId)) {
      setClaimAttachmentId('')
    }
  }, [claimAttachmentId, workerReceipts])

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
      if (workerCanUpload && isReceiptMediaType(created.mediaType)) {
        setClaimAttachmentId(String(created.id))
      }
      onSuccess?.(workerCanUpload
        ? 'Paragon lub dowód wydatku został prywatnie dodany do zlecenia.'
        : 'Załącznik został bezpiecznie dodany do zlecenia.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się dodać załącznika.')
    } finally {
      setBusyAction('')
    }
  }

  async function handleCreateClaim(event) {
    event.preventDefault()
    const amount = Number(claimAmount)
    const attachmentId = Number(claimAttachmentId)
    if (!Number.isFinite(amount) || amount <= 0) {
      setError('Podaj prawidłową kwotę wydatku większą od 0 zł.')
      return
    }
    if (amount > remainingExpenseBudget + 0.000001) {
      setError(`Kwota przekracza pozostały budżet ${moneyFormatter.format(remainingExpenseBudget)}.`)
      return
    }
    if (!Number.isInteger(attachmentId) || attachmentId <= 0) {
      setError('Wybierz prywatny paragon dodany przez Ciebie.')
      return
    }

    setBusyAction('expense-claim')
    setError('')
    try {
      await createJobExpenseClaim(job.id, amount.toFixed(2), attachmentId)
      setClaimAmount('')
      setClaimAttachmentId('')
      setExpenseSummary(await getJobExpenseSummary(job.id, { cache: 'no-store' }))
      onSuccess?.('Wydatek został zgłoszony i zabezpieczony paragonem. Zwrot nastąpi przy rozliczeniu zlecenia.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zgłosić wydatku.')
    } finally {
      setBusyAction('')
    }
  }

  async function handleDownload(attachment) {
    setBusyAction(`download-${attachment.id}`)
    setError('')
    try {
      const blob = await downloadJobAttachment(job.id, attachment.id, { cache: 'no-store' })
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
          <h2>Załączniki i wydatki</h2>
        </div>
        <span className="job-attachment-panel__count">
          {isCreator ? `${attachments.length} / 12` : `${attachments.length} dostępnych`}
        </span>
      </div>

      <p className="job-attachment-panel__intro">
        Dozwolone: JPG, PNG, WebP i PDF do 10 MB. Serwer sprawdza rzeczywisty format pliku i przechowuje zawartość zaszyfrowaną.
      </p>

      {isParticipant && expenseLoading && <div className="page-state">Pobieranie budżetu wydatków…</div>}

      {isParticipant && !expenseLoading && expenseSummary && Number(expenseSummary.budgetAmount || 0) > 0 && (
        <div className="job-expense-summary" role="region" aria-label="Rozliczenie wydatków">
          <div className="job-expense-summary__heading">
            <div>
              <span className="eyebrow">Budżet zakupowy</span>
              <strong>{moneyFormatter.format(Number(expenseSummary.budgetAmount || 0))}</strong>
            </div>
            <span className="job-attachment-panel__count">
              {EXPENSE_STATUS_LABELS[expenseSummary.status] || expenseSummary.status || 'Brak blokady'}
            </span>
          </div>
          <div className="job-expense-summary__metrics">
            <div><span>Zgłoszono</span><strong>{moneyFormatter.format(Number(expenseSummary.claimedAmount || 0))}</strong></div>
            <div><span>Pozostało</span><strong>{moneyFormatter.format(remainingExpenseBudget)}</strong></div>
            <div><span>Zwrócono wykonawcy</span><strong>{moneyFormatter.format(Number(expenseSummary.reimbursedAmount || 0))}</strong></div>
            <div><span>Zwrócono zlecającemu</span><strong>{moneyFormatter.format(Number(expenseSummary.refundedAmount || 0))}</strong></div>
          </div>
          {expenseSummary.claims?.length > 0 && (
            <div className="job-expense-claims">
              <strong>Zgłoszone wydatki</strong>
              {expenseSummary.claims.map((claim) => (
                <div className="job-expense-claim-row" key={claim.id}>
                  <span>Paragon #{claim.attachmentId}</span>
                  <span>{dateFormatter.format(new Date(claim.createdAt))}</span>
                  <strong>{moneyFormatter.format(Number(claim.amount))}</strong>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {workerCanUpload && (
        <div className="job-attachment-panel__locked-note" role="note">
          Jako wykonawca możesz podczas realizacji dodać wyłącznie prywatny materiał dla stron zlecenia, np. paragon potrzebny do rozliczenia kosztów.
        </div>
      )}

      {error && <div className="form-message form-message--error" role="alert">{error}</div>}

      {canUpload && (
        <form className="job-attachment-upload" onSubmit={handleUpload}>
          <fieldset>
            <legend>Kto ma zobaczyć plik?</legend>
            <div className="job-attachment-visibility-grid">
              {availableVisibilityOptions.map((option) => (
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
                    disabled={Boolean(busyAction) || workerCanUpload}
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
              {busyAction === 'upload' ? 'Szyfrowanie i wysyłanie…' : workerCanUpload ? 'Dodaj dowód wydatku' : 'Dodaj załącznik'}
            </button>
          </div>
        </form>
      )}

      {isWorker && job.status === 'IN_PROGRESS' && expenseSummary?.status === 'HELD' && Number(expenseSummary.budgetAmount || 0) > 0 && (
        <form className="job-expense-claim-form" onSubmit={handleCreateClaim}>
          <div>
            <span className="eyebrow">Zwrot kosztów</span>
            <h3>Zgłoś wydatek z paragonem</h3>
            <p>Każdy paragon może zostać użyty tylko raz. Łączna kwota zgłoszeń nie może przekroczyć zabezpieczonego budżetu.</p>
          </div>
          <div className="job-expense-claim-form__fields">
            <label className="field">
              <span>Kwota</span>
              <input
                type="number"
                min="0.01"
                max={remainingExpenseBudget.toFixed(2)}
                step="0.01"
                inputMode="decimal"
                value={claimAmount}
                onChange={(event) => setClaimAmount(event.target.value)}
                disabled={Boolean(busyAction) || remainingExpenseBudget <= 0}
                required
              />
              <small>Pozostały budżet: {moneyFormatter.format(remainingExpenseBudget)}</small>
            </label>
            <label className="field">
              <span>Paragon</span>
              <select
                value={claimAttachmentId}
                onChange={(event) => setClaimAttachmentId(event.target.value)}
                disabled={Boolean(busyAction) || workerReceipts.length === 0}
                required
              >
                <option value="">Wybierz prywatny dowód wydatku</option>
                {workerReceipts.map((attachment) => (
                  <option key={attachment.id} value={attachment.id}>
                    #{attachment.id} · {attachment.originalFilename}
                  </option>
                ))}
              </select>
              <small>{workerReceipts.length ? 'Po zgłoszeniu dowód zostanie zachowany jako dokument finansowy.' : 'Najpierw dodaj powyżej prywatny paragon JPG, PNG, WebP lub PDF.'}</small>
            </label>
            <button
              className="button button--primary"
              type="submit"
              disabled={Boolean(busyAction) || !claimAmount || !claimAttachmentId || remainingExpenseBudget <= 0}
            >
              {busyAction === 'expense-claim' ? 'Zgłaszanie…' : 'Zgłoś wydatek'}
            </button>
          </div>
        </form>
      )}

      {(isCreator || isWorker) && !canUpload && (
        <div className="job-attachment-panel__locked-note">
          Nowych plików nie można już dodawać na tym etapie zlecenia.
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
            const claimedReceipt = claimedAttachmentIds.has(attachment.id)
            const canDelete = isCreator && !claimedReceipt && (secret || !job.takenById)
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
                    {claimedReceipt && <span>Dowód rozliczenia · zachowany</span>}
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

function isReceiptMediaType(mediaType) {
  return ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'].includes(mediaType)
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
