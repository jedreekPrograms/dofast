import { useState } from 'react'
import { reportJob } from '../api/jobReportsApi.js'
import './ReportJobDialog.css'

const REASONS = [
  { value: 'SPAM', label: 'Spam lub duplikat' },
  { value: 'FRAUD', label: 'Próba oszustwa' },
  { value: 'PROHIBITED_CONTENT', label: 'Niedozwolona treść lub usługa' },
  { value: 'HARASSMENT', label: 'Nękanie lub obraźliwa treść' },
  { value: 'OTHER', label: 'Inny powód' },
]

function ReportJobDialog({ job, onClose, onSubmitted }) {
  const [reason, setReason] = useState('SPAM')
  const [details, setDetails] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return

    setSubmitting(true)
    setError('')

    try {
      const report = await reportJob(job.id, {
        reason,
        details: details.trim() || null,
      })
      onSubmitted(report)
    } catch (requestError) {
      if (requestError.status === 409) {
        setError('To zlecenie zostało już przez Ciebie zgłoszone.')
      } else if (requestError.status === 403) {
        setError('Nie możesz zgłosić własnego zlecenia.')
      } else {
        setError(requestError.message || 'Nie udało się wysłać zgłoszenia.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="report-job-dialog__backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="report-job-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-job-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="report-job-dialog__header">
          <div>
            <span className="eyebrow">Bezpieczeństwo</span>
            <h2 id="report-job-title">Zgłoś zlecenie #{job.id}</h2>
          </div>
          <button className="report-job-dialog__close" type="button" onClick={onClose} aria-label="Zamknij" disabled={submitting}>×</button>
        </div>

        <p className="report-job-dialog__intro">
          Zgłoszenie trafi do kolejki moderatorów. Samo wysłanie zgłoszenia nie usuwa automatycznie oferty ani nie blokuje użytkownika.
        </p>

        {error && <div className="form-message form-message--error" role="alert">{error}</div>}

        <form className="report-job-dialog__form" onSubmit={handleSubmit}>
          <label htmlFor="job-report-reason">Powód zgłoszenia</label>
          <select
            id="job-report-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            disabled={submitting}
          >
            {REASONS.map((item) => (
              <option key={item.value} value={item.value}>{item.label}</option>
            ))}
          </select>

          <label htmlFor="job-report-details">Dodatkowe informacje <span>(opcjonalnie)</span></label>
          <textarea
            id="job-report-details"
            rows={5}
            maxLength={1000}
            value={details}
            onChange={(event) => setDetails(event.target.value)}
            placeholder="Opisz krótko, co powinien sprawdzić moderator. Nie podawaj danych płatniczych ani innych wrażliwych danych."
            disabled={submitting}
          />
          <div className="report-job-dialog__counter">{details.length}/1000</div>

          <div className="report-job-dialog__actions">
            <button className="button button--secondary" type="button" onClick={onClose} disabled={submitting}>Anuluj</button>
            <button className="button button--danger" type="submit" disabled={submitting}>
              {submitting ? 'Wysyłanie…' : 'Wyślij zgłoszenie'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}

export default ReportJobDialog
