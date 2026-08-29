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
      if (!shouldAwaitSettlement(initial, redirectStatusRef.current)) return

      if (redirectStatusRef.current === 'failed') {
        setError('Stripe nie potwierdził płatności. Publikacja nadal czeka na finansowanie i możesz bezpiecznie spróbować ponownie.')
        return
      }

      for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))
        const latest = await refresh()
        if (!shouldAwaitSettlement(latest, redirectStatusRef.current)) return
      }

      if (initial.status === 'CANCELLED') {
        setError('Publikacja jest anulowana, ale backend nadal nie potwierdził wyniku płatności Stripe. Nie ponawiaj płatności w ciemno — sprawdź stan ponownie za chwilę.')
      } else {
        setError('Backend nadal czeka na podpisane potwierdzenie Stripe. Nie ponawiaj płatności w ciemno — sprawdź stan ponownie za chwilę.')
      }
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
  const outcome = recoveryOutcome(publication)

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

        {!checking && outcome && (
          <div className="job-publication-payment__note">
            <strong>{outcome.title}</strong>
            <div>{outcome.message}</div>
          </div>
        )}

        {error && <div className="job-publication-payment__message job-publication-payment__message--error">{error}</div>}

        <div className="job-publication-payment__actions">
          {!checking && shouldAwaitSettlement(publication, redirectStatusRef.current) && !canResumeFunding && (
            <button type="button" className="button button--primary" onClick={recover}>
              Sprawdź ponownie
            </button>
          )}
          {!checking && canResumeFunding && (
            <button type="button" className="button button--primary" onClick={() => navigate('/jobs/new', { replace: true })}>
              Wróć do bezpiecznej płatności
            </button>
          )}
          {!checking && terminalWithoutJob && outcome?.paymentReceived && (
            <button type="button" className="button button--secondary" onClick={() => navigate('/wallet')}>
              Zobacz portfel
            </button>
          )}
          {!checking && terminalWithoutJob && (
            <button type="button" className="button button--primary" onClick={() => navigate('/jobs/new', { replace: true })}>
              {outcome?.actionLabel || 'Wróć do formularza'}
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

function shouldAwaitSettlement(publication, redirectStatus) {
  if (!publication) return false
  if (publication.status === 'PAYMENT_REQUIRED') return true
  return publication.status === 'CANCELLED'
    && !publication.paymentReceivedAt
    && (redirectStatus === 'succeeded' || redirectStatus === 'processing')
}

function recoveryOutcome(publication) {
  if (!publication) return null

  if (publication.status === 'PAYMENT_RECEIVED') {
    if (publication.recoveryReason === 'CATEGORY_UNAVAILABLE') {
      return {
        title: 'Płatność jest bezpiecznie w portfelu',
        message: 'Kategoria zlecenia przestała być dostępna przed finalizacją. Zlecenie nie zostało utworzone, a potwierdzona płatność pozostała jako saldo doFast. Wybierz aktualną kategorię i opublikuj ponownie bez ponownego doładowania tej kwoty.',
        actionLabel: 'Popraw i opublikuj ponownie',
        paymentReceived: true,
      }
    }
    if (publication.recoveryReason === 'ROUTE_QUOTE_UNAVAILABLE') {
      return {
        title: 'Płatność jest bezpiecznie w portfelu',
        message: 'Wycena trasy nie była już ważna przy finalizacji. Zlecenie nie zostało utworzone, a potwierdzona płatność pozostała jako saldo doFast. Wyznacz aktualną trasę i rozpocznij publikację ponownie.',
        actionLabel: 'Wyznacz trasę ponownie',
        paymentReceived: true,
      }
    }
    if (publication.recoveryReason === 'PUBLICATION_EXPIRED') {
      return {
        title: 'Płatność dotarła po zamknięciu publikacji',
        message: 'Zlecenie nie zostało utworzone, ponieważ bezpieczne okno publikacji już wygasło. Stripe został jednak rozliczony dokładnie raz, a środki są dostępne w portfelu doFast do ponownej publikacji.',
        actionLabel: 'Opublikuj ponownie',
        paymentReceived: true,
      }
    }
    return {
      title: 'Płatność jest bezpiecznie w portfelu',
      message: 'Płatność została potwierdzona, ale tej próby publikacji nie można było bezpiecznie dokończyć. Zlecenie nie powstało, a środki pozostały w portfelu doFast. Nie wykonuj ponownego doładowania tej samej kwoty.',
      actionLabel: 'Przygotuj publikację ponownie',
      paymentReceived: true,
    }
  }

  if (publication.status === 'CANCELLED' && publication.paymentReceivedAt) {
    return {
      title: 'Płatność dotarła po anulowaniu',
      message: 'Publikacja była już anulowana, gdy backend otrzymał podpisane potwierdzenie Stripe. Zlecenie nie zostało wskrzeszone, a pobrana kwota została zaksięgowana w portfelu doFast.',
      actionLabel: 'Utwórz nowe zlecenie',
      paymentReceived: true,
    }
  }

  if (publication.status === 'CANCELLED') {
    return {
      title: 'Publikacja została anulowana',
      message: 'Zlecenie nie zostało utworzone, a rezerwacja salda tej publikacji została zwolniona. Jeśli Stripe potwierdzi późną płatność, backend zaksięguje ją w portfelu zamiast wskrzeszać anulowane zlecenie.',
      actionLabel: 'Wróć do formularza',
      paymentReceived: false,
    }
  }

  return null
}

function readAndClearStripeReturnStatus() {
  const url = new URL(window.location.href)
  const redirectStatus = url.searchParams.get('redirect_status')
  if (url.search) window.history.replaceState(window.history.state, '', url.pathname + url.hash)
  return redirectStatus
}

function publicationStatusLabel(status) {
  if (status === 'PUBLISHED') return 'Opublikowane'
  if (status === 'PAYMENT_RECEIVED') return 'Płatność zaksięgowana w portfelu'
  if (status === 'CANCELLED') return 'Anulowane'
  return 'Oczekuje na płatność'
}

function money(value, currency = 'PLN') {
  return Number(value || 0).toLocaleString('pl-PL', { style: 'currency', currency })
}

export default JobPublicationReturnPage
