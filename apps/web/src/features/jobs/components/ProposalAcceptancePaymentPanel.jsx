import { useEffect, useRef, useState } from 'react'
import { loadStripeScript, STRIPE_PUBLISHABLE_KEY } from '../../../shared/payments/stripeClient.js'
import { createWalletTopUpIntent } from '../../wallet/api/walletApi.js'
import { buildProposalAcceptanceReturnUrl } from '../payments/proposalAcceptanceReturn.js'

const priceFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

function createRequestId(jobId, proposalId) {
  if (window.crypto?.randomUUID) {
    return `proposal_accept_${jobId}_${proposalId}_${window.crypto.randomUUID()}`
  }
  return `proposal_accept_${jobId}_${proposalId}_${Date.now()}_${Math.random().toString(36).slice(2)}`
}

function ProposalAcceptancePaymentPanel({ funding, onPaymentSubmitted, onCancel }) {
  const [intent, setIntent] = useState(null)
  const [preparing, setPreparing] = useState(false)
  const [paymentReady, setPaymentReady] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const requestIdRef = useRef(null)
  const mountRef = useRef(null)
  const stripeRef = useRef(null)
  const elementsRef = useRef(null)
  const paymentElementRef = useRef(null)

  if (!requestIdRef.current) {
    requestIdRef.current = createRequestId(funding.jobId, funding.proposalId)
  }

  useEffect(() => {
    if (!intent?.clientSecret || !STRIPE_PUBLISHABLE_KEY) return undefined

    let active = true
    setPaymentReady(false)

    loadStripeScript()
      .then((Stripe) => {
        if (!active || !mountRef.current) return
        const stripe = Stripe(STRIPE_PUBLISHABLE_KEY)
        const elements = stripe.elements({
          clientSecret: intent.clientSecret,
          appearance: {
            theme: 'stripe',
            variables: {
              borderRadius: '12px',
              fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif',
            },
          },
        })
        const paymentElement = elements.create('payment', { layout: 'tabs' })
        stripeRef.current = stripe
        elementsRef.current = elements
        paymentElementRef.current = paymentElement
        paymentElement.mount(mountRef.current)
        setPaymentReady(true)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się uruchomić formularza Stripe.')
      })

    return () => {
      active = false
      paymentElementRef.current?.unmount()
      paymentElementRef.current = null
      elementsRef.current = null
      stripeRef.current = null
    }
  }, [intent?.clientSecret])

  async function handlePrepare() {
    setPreparing(true)
    setError('')
    try {
      const prepared = await createWalletTopUpIntent(
        Number(funding.stripeChargeAmount).toFixed(2),
        requestIdRef.current,
      )
      setIntent(prepared)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się przygotować dopłaty.')
    } finally {
      setPreparing(false)
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!stripeRef.current || !elementsRef.current) return

    setSubmitting(true)
    setError('')
    try {
      const returnUrl = buildProposalAcceptanceReturnUrl(
        window.location.origin,
        funding.jobId,
        funding.proposalId,
      )
      const { error: stripeError, paymentIntent } = await stripeRef.current.confirmPayment({
        elements: elementsRef.current,
        confirmParams: { return_url: returnUrl },
        redirect: 'if_required',
      })
      if (stripeError) {
        setError(stripeError.message || 'Stripe odrzucił próbę płatności.')
        return
      }
      if (paymentIntent?.status === 'succeeded' || paymentIntent?.status === 'processing') {
        onPaymentSubmitted?.()
        return
      }
      setError('Płatność nie została jeszcze zakończona. Spróbuj ponownie lub wybierz inną metodę.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się potwierdzić dopłaty.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="proposal-acceptance-payment" role="region" aria-label="Dopłata do wybranej propozycji">
      <div className="proposal-acceptance-payment__heading">
        <div>
          <strong>Uzupełnij escrow przed wyborem wykonawcy</strong>
          <p>
            W escrow jest {priceFormatter.format(Number(funding.currentEscrowAmount))}, a wybrana propozycja wynosi{' '}
            {priceFormatter.format(Number(funding.targetEscrowAmount))}.
          </p>
        </div>
        <button className="button button--secondary" type="button" onClick={onCancel} disabled={preparing || submitting}>
          Anuluj
        </button>
      </div>

      <div className="proposal-acceptance-payment__summary">
        <span>
          <small>Z obecnego portfela</small>
          <strong>{priceFormatter.format(Number(funding.walletContributionAvailable))}</strong>
        </span>
        <span>
          <small>Brakująca kwota</small>
          <strong>{priceFormatter.format(Number(funding.paymentShortfall))}</strong>
        </span>
        <span>
          <small>Płatność Stripe</small>
          <strong>{priceFormatter.format(Number(funding.stripeChargeAmount))}</strong>
        </span>
      </div>

      {Number(funding.stripeChargeAmount) > Number(funding.paymentShortfall) && (
        <p className="proposal-acceptance-payment__note">
          Minimalna płatność online to 1,00 zł. Nadwyżka pozostanie w Twoim portfelu; escrow pobierze wyłącznie kwotę potrzebną do zaakceptowanej ceny.
        </p>
      )}

      {!funding.onlinePaymentAvailable && (
        <div className="proposal-acceptance-payment__warning">
          Brakująca kwota przekracza limit pojedynczego doładowania online 10 000 zł. Doładuj portfel osobno, a potem wróć i ponownie wybierz tę propozycję.
        </div>
      )}

      {funding.onlinePaymentAvailable && !STRIPE_PUBLISHABLE_KEY && (
        <div className="proposal-acceptance-payment__warning">
          Płatności Stripe nie są skonfigurowane w tej instancji. Doładuj portfel na stronie portfela i wróć do propozycji.
        </div>
      )}

      {!intent && funding.onlinePaymentAvailable && STRIPE_PUBLISHABLE_KEY && (
        <button className="button button--primary" type="button" onClick={handlePrepare} disabled={preparing}>
          {preparing ? 'Przygotowywanie płatności…' : `Dopłać ${priceFormatter.format(Number(funding.stripeChargeAmount))}`}
        </button>
      )}

      {intent && (
        <form className="proposal-acceptance-payment__stripe" onSubmit={handleSubmit}>
          <div ref={mountRef} className="proposal-acceptance-payment__stripe-element" />
          <button className="button button--primary" type="submit" disabled={!paymentReady || submitting}>
            {submitting ? 'Potwierdzanie…' : 'Zapłać i wybierz wykonawcę'}
          </button>
        </form>
      )}

      {error && <div className="form-message form-message--error" role="alert">{error}</div>}
      <p className="proposal-acceptance-payment__footnote">
        Płatność najpierw zasila Twój portfel przez podpisany webhook Stripe. Dopiero potem doFast ponownie sprawdzi zlecenie, propozycję i saldo oraz spróbuje atomowo zwiększyć escrow i przypisać wykonawcę.
      </p>
      <a href="/wallet" className="proposal-acceptance-payment__wallet-link">Przejdź do portfela</a>
    </div>
  )
}

export default ProposalAcceptancePaymentPanel
