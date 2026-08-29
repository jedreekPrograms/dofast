import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getJobPublication } from '../api/jobsApi.js'

const POLL_INTERVAL_MS = 1000
const MAX_POLL_ATTEMPTS = 45

function JobPublicationReturnPage() {
  const navigate = useNavigate()
  const { publicationId } = useParams()
  const parsedPublicationId = Number(publicationId)
  const redirectStatusRef = useRef(readAndClearStripeReturnStatus())
  const [publication, setPublication] = useState(null)
  const [checking, setChecking] = useState(true)
  const [error, setError] = useState('')

  const refresh = useCallback(async () => {
    if (!Number.isSafeInteger(parsedPublicationId) || parsedPublicationId <= 0) {
      throw new Error('Nieprawidłowy identyfikator publikacji.')
    }
    const latest = await getJobPublication(parsedPublicationId)
    setPublication(latest)
    if (latest.status === 'PUBLISHED' && latest.jobId) {
      navigate(`/jobs/${latest.jobId}`, { replace: true })
    }
    return latest
  }, [navigate, parsedPublicationId])

  const recover = useCallback(async () => {
    setChecking(true)
    setError('')
    try {
      const initial = await refresh()
      if (initial.status !== 'PAYMENT_REQUIRED') return

      if (redirectStatusRef.current === 'failed') {
        setError('Stripe nie potwierdził płatności. Publikacja nadal czeka na finansowanie i możesz bezpiecznie spróbować ponownie.')
        return
      }

      for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))
        const latest = await refresh()
        if (latest.status !== 'PAYMENT_REQUIRED') return
      }

      setError('Backend nadal czeka na podpisane potwierdzenie Stripe. Nie ponawiaj płatności w ciemno — sprawdź stan ponownie za chwilę.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się odzyskać stanu publikacji po płatności.')
    } finally {
      setChecking(false)
    }
  }, [refresh])

  useEffect(() => {
    recover()
  }, [recover])

  const canResumeFunding = publication?.status === 'PAYMENT_REQUIRED' && redirectStatusRef.current === 'failed'
  const terminalWithoutJob = publication?.status === 'PAYMENT_RECEIVED' || publication?.status === 'CANCELLED'

  return (
    <main className="create-job-page">
      <header className="page-heading">
        <span className="eyebrow">Finansowanie zlecenia</span>
        <h1>Potwierdzamy wynik płatności</h1>
        <p>Status z przeglądarki nie rozstrzyga o pieniądzach. doFast czeka na autorytatywne potwierdzenie backendu i podpisany webhook Stripe.</p>
      </header>

      <section className="panel job-publication-payment" aria-live="polite">
        {checking && (
          <div className="job-publication-payment__note">
            Sprawdzamy stan publikacji i rozliczenie Stripe…
          </div>
        )}

        {publication && (
          <div className="job-publication-payment__summary">
            <div><span>Publikacja</span><strong>#{publication.id}</strong></div>
            <div><span>Status serwera</span><strong>{publicationStatusLabel(publication.status)}</strong></div>
            <div><span>Zabezpieczona kwota</span><strong>{money(publication.totalAmount, publication.currency)}</strong></div>
            <div><span>Płatność Stripe</span><strong>{money(publication.paymentAmount, publication.currency)}</strong></div>
          </div>
        )}

        {error && <div className="job-publication-payment__message job-publication-payment__message--error">{error}</div>}

        <div className="job-publication-payment__actions">
          {!checking && publication?.status === 'PAYMENT_REQUIRED' && !canResumeFunding && (
            <button type="button" className="button button--primary" onClick={recover}>
              Sprawdź ponownie
            </button>
          )}
          {canResumeFunding && (
            <button type="button" className="button button--primary" onClick={() => navigate('/jobs/new', { replace: true })}>
              Wróć do bezpiecznej płatności
            </button>
          )}
          {terminalWithoutJob && (
            <button type="button" className="button button--primary" onClick={() => navigate('/jobs/new', { replace: true })}>
              Wróć do formularza
            </button>
          )}
        </div>

        <small className="job-publication-payment__footnote">
          Parametry powrotne Stripe są usuwane z paska adresu natychmiast po wejściu na tę stronę. Publikacja następuje wyłącznie na podstawie stanu serwera.
        </small>
      </section>
    </main>
  )
}

function readAndClearStripeReturnStatus() {
  const url = new URL(window.location.href)
  const redirectStatus = url.searchParams.get('redirect_status')
  if (url.search) window.history.replaceState(window.history.state, '', url.pathname + url.hash)
  return redirectStatus
}

function publicationStatusLabel(status) {
  if (status === 'PUBLISHED') return 'Opublikowane'
  if (status === 'PAYMENT_RECEIVED') return 'Płatność zaksięgowana'
  if (status === 'CANCELLED') return 'Anulowane'
  return 'Oczekuje na płatność'
}

function money(value, currency = 'PLN') {
  return Number(value || 0).toLocaleString('pl-PL', { style: 'currency', currency })
}

export default JobPublicationReturnPage
