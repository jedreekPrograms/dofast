import { useEffect, useLayoutEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { acceptJobProposal, getJobProposalAcceptanceFunding } from '../api/jobsApi.js'
import { readProposalAcceptanceReturn } from '../payments/proposalAcceptanceReturn.js'

const MAX_POLL_ATTEMPTS = 40
const POLL_INTERVAL_MS = 750
const FAILED_REDIRECT_STATUSES = new Set(['failed', 'requires_payment_method'])
const CHECKING_MESSAGE = 'Sprawdzamy zaksięgowanie dopłaty i ponownie weryfikujemy możliwość wyboru wykonawcy…'
const INVALID_CONTEXT_MESSAGE = 'Nie można bezpiecznie wznowić wyboru wykonawcy, ponieważ brakuje prawidłowego kontekstu płatności. Jeśli Stripe pobrał środki, pozostają one w portfelu doFast.'

function positiveInteger(value) {
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function sleep(milliseconds) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

function ProposalAcceptanceReturnPage() {
  const { jobId } = useParams()
  const parsedJobId = positiveInteger(jobId)
  const stripeReturn = useMemo(() => readProposalAcceptanceReturn(window.location.href), [])
  const proposalId = stripeReturn?.proposalId
  const validContext = Boolean(parsedJobId && proposalId)
  const [phase, setPhase] = useState(validContext ? 'checking' : 'error')
  const [message, setMessage] = useState(validContext ? CHECKING_MESSAGE : INVALID_CONTEXT_MESSAGE)
  const [accepted, setAccepted] = useState(null)
  const [retryToken, setRetryToken] = useState(0)

  useLayoutEffect(() => {
    if (!stripeReturn) return
    window.history.replaceState(
      window.history.state,
      document.title,
      stripeReturn.sanitizedLocation,
    )
  }, [stripeReturn])

  useEffect(() => {
    if (!validContext) return undefined

    const controller = new AbortController()
    let active = true

    async function recover() {
      const redirectFailed = FAILED_REDIRECT_STATUSES.has(stripeReturn.redirectStatus)
      const maxAttempts = redirectFailed ? 1 : MAX_POLL_ATTEMPTS

      try {
        for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
          if (attempt > 0) {
            await sleep(POLL_INTERVAL_MS)
            if (!active) return
          }

          const funding = await getJobProposalAcceptanceFunding(
            parsedJobId,
            proposalId,
            { signal: controller.signal },
          )
          if (!active) return

          if (!funding.paymentRequired) {
            const result = await acceptJobProposal(parsedJobId, proposalId)
            if (!active) return
            setAccepted(result)
            setPhase('success')
            setMessage('Dopłata jest zaksięgowana, a backend ponownie zweryfikował propozycję i atomowo przypisał wykonawcę.')
            return
          }
        }

        if (!active) return
        if (redirectFailed) {
          setPhase('error')
          setMessage('Stripe nie zakończył tej próby płatności, a backend nie widzi wymaganych środków w portfelu. Możesz wrócić do zlecenia i spróbować ponownie inną metodą.')
          return
        }

        setPhase('pending')
        setMessage('Backend nadal nie widzi końcowego rozliczenia Stripe. Nie ponawiaj płatności w ciemno — jeśli środki zostaną zaksięgowane, pozostaną bezpiecznie w portfelu i możesz sprawdzić stan ponownie.')
      } catch (requestError) {
        if (!active || requestError.name === 'AbortError') return
        setPhase('error')
        setMessage(`${requestError.message || 'Nie udało się bezpiecznie dokończyć wyboru wykonawcy.'} Jeśli płatność Stripe została przyjęta, środki pozostają w portfelu doFast; webhook nigdy sam nie wybiera wykonawcy.`)
      }
    }

    recover()
    return () => {
      active = false
      controller.abort()
    }
  }, [parsedJobId, proposalId, retryToken, stripeReturn, validContext])

  const canRetry = Boolean(validContext && phase !== 'checking' && phase !== 'success')

  function handleRetry() {
    setPhase('checking')
    setMessage(CHECKING_MESSAGE)
    setAccepted(null)
    setRetryToken((current) => current + 1)
  }

  return (
    <main className="create-job-page">
      <header className="page-heading">
        <span className="eyebrow">Wybór wykonawcy</span>
        <h1>Kończymy finansowanie propozycji</h1>
        <p>Płatność zasila portfel przez podpisany webhook Stripe. Dopiero później doFast ponownie sprawdza stan zlecenia, propozycji i escrow.</p>
      </header>

      <section className="panel job-publication-payment" aria-live="polite">
        <div className="job-publication-payment__summary">
          <div><span>Zlecenie</span><strong>{parsedJobId ? `#${parsedJobId}` : '—'}</strong></div>
          <div><span>Propozycja</span><strong>{proposalId ? `#${proposalId}` : '—'}</strong></div>
          <div><span>Stan odzyskiwania</span><strong>{phaseLabel(phase)}</strong></div>
        </div>

        <div className={`form-message ${phase === 'success' ? 'form-message--success' : phase === 'checking' || phase === 'pending' ? '' : 'form-message--error'}`} role="status">
          {message}
        </div>

        {accepted?.job && (
          <div className="job-publication-payment__note">
            <strong>Wykonawca został wybrany.</strong>
            <div>Finalne wynagrodzenie: {Number(accepted.job.price).toLocaleString('pl-PL', { style: 'currency', currency: 'PLN' })}.</div>
          </div>
        )}

        <div className="job-publication-payment__actions">
          {canRetry && (
            <button type="button" className="button button--primary" onClick={handleRetry}>
              Sprawdź ponownie
            </button>
          )}
          {parsedJobId && <Link className="button button--secondary" to={`/jobs/${parsedJobId}`}>Wróć do zlecenia</Link>}
          <Link className="button button--secondary" to="/wallet">Zobacz portfel</Link>
        </div>

        <small className="job-publication-payment__footnote">
          Parametry Stripe, w tym <code>payment_intent_client_secret</code>, są usuwane z paska adresu przed rozpoczęciem odzyskiwania. Status przeglądarki nie kredytuje portfela, nie zmienia escrow i nie wybiera wykonawcy.
        </small>
      </section>
    </main>
  )
}

function phaseLabel(phase) {
  if (phase === 'success') return 'Zakończone'
  if (phase === 'pending') return 'Oczekiwanie na Stripe'
  if (phase === 'error') return 'Wymaga uwagi'
  return 'Sprawdzanie'
}

export default ProposalAcceptanceReturnPage
