import { useCallback, useEffect, useRef, useState } from 'react'
import { createWalletTopUpIntent } from '../api/walletApi.js'

const STRIPE_SCRIPT_URL = 'https://js.stripe.com/v3/'
const STRIPE_PUBLISHABLE_KEY = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY?.trim()
const PRESET_AMOUNTS = [20, 50, 100, 200]
const MIN_TOP_UP = 1
const MAX_TOP_UP = 10000

let stripeScriptPromise = null

function loadStripeScript() {
  if (window.Stripe) return Promise.resolve(window.Stripe)
  if (stripeScriptPromise) return stripeScriptPromise

  stripeScriptPromise = new Promise((resolve, reject) => {
    let script = document.querySelector('script[data-dofast-stripe]')

    const handleLoad = () => {
      if (window.Stripe) {
        resolve(window.Stripe)
      } else {
        stripeScriptPromise = null
        reject(new Error('Stripe.js załadował się bez globalnego klienta Stripe.'))
      }
    }

    const handleError = () => {
      stripeScriptPromise = null
      reject(new Error('Nie udało się załadować Stripe.js.'))
    }

    if (!script) {
      script = document.createElement('script')
      script.src = STRIPE_SCRIPT_URL
      script.async = true
      script.dataset.dofastStripe = 'true'
      document.head.appendChild(script)
    }

    script.addEventListener('load', handleLoad, { once: true })
    script.addEventListener('error', handleError, { once: true })
  })

  return stripeScriptPromise
}

function createRequestId() {
  if (window.crypto?.randomUUID) {
    return `topup_${window.crypto.randomUUID()}`
  }
  return `topup_${Date.now()}_${Math.random().toString(36).slice(2)}`
}

function clearStripeReturnParams() {
  const url = new URL(window.location.href)
  const stripeParams = ['topup', 'payment_intent', 'payment_intent_client_secret', 'redirect_status']
  stripeParams.forEach((key) => url.searchParams.delete(key))
  window.history.replaceState({}, document.title, `${url.pathname}${url.search}${url.hash}`)
}

function messageForPaymentStatus(status) {
  switch (status) {
    case 'succeeded':
      return ['success', 'Płatność zakończona. Stripe potwierdził wpłatę; saldo zostanie zaksięgowane przez webhook.']
    case 'processing':
      return ['info', 'Płatność jest przetwarzana. Saldo pojawi się po potwierdzeniu Stripe.']
    case 'requires_payment_method':
      return ['error', 'Płatność nie została zakończona. Wybierz inną metodę płatności i spróbuj ponownie.']
    default:
      return ['info', 'Płatność została przekazana do Stripe. Oczekujemy na jej końcowy status.']
  }
}

function StripeTopUpPanel({ onPaymentSettled }) {
  const [amount, setAmount] = useState('50.00')
  const [intent, setIntent] = useState(null)
  const [preparing, setPreparing] = useState(false)
  const [paymentReady, setPaymentReady] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState('')
  const [messageTone, setMessageTone] = useState('info')

  const mountRef = useRef(null)
  const stripeRef = useRef(null)
  const elementsRef = useRef(null)
  const paymentElementRef = useRef(null)

  const showStatus = useCallback((tone, text) => {
    setMessageTone(tone)
    setMessage(text)
  }, [])

  const resetPaymentForm = useCallback(() => {
    paymentElementRef.current?.unmount()
    paymentElementRef.current = null
    elementsRef.current = null
    stripeRef.current = null
    setPaymentReady(false)
    setIntent(null)
  }, [])

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
      .catch((error) => {
        if (active) showStatus('error', error.message || 'Nie udało się uruchomić formularza Stripe.')
      })

    return () => {
      active = false
      paymentElementRef.current?.unmount()
      paymentElementRef.current = null
      elementsRef.current = null
      stripeRef.current = null
    }
  }, [intent?.clientSecret, showStatus])

  useEffect(() => {
    if (!STRIPE_PUBLISHABLE_KEY) return undefined

    const params = new URLSearchParams(window.location.search)
    const clientSecret = params.get('payment_intent_client_secret')
    if (!clientSecret) return undefined

    let active = true
    showStatus('info', 'Sprawdzamy status płatności po powrocie ze Stripe…')

    loadStripeScript()
      .then((Stripe) => Stripe(STRIPE_PUBLISHABLE_KEY).retrievePaymentIntent(clientSecret))
      .then(({ paymentIntent, error }) => {
        if (!active) return
        if (error) {
          showStatus('error', error.message || 'Nie udało się sprawdzić statusu płatności.')
          return
        }
        const [tone, text] = messageForPaymentStatus(paymentIntent?.status)
        showStatus(tone, text)
        if (paymentIntent?.status === 'succeeded' || paymentIntent?.status === 'processing') {
          onPaymentSettled?.()
        }
      })
      .catch((error) => {
        if (active) showStatus('error', error.message || 'Nie udało się sprawdzić statusu płatności.')
      })
      .finally(() => {
        if (active) clearStripeReturnParams()
      })

    return () => { active = false }
  }, [onPaymentSettled, showStatus])

  async function handlePreparePayment() {
    const parsedAmount = Number(String(amount).replace(',', '.'))
    if (!Number.isFinite(parsedAmount) || parsedAmount < MIN_TOP_UP || parsedAmount > MAX_TOP_UP) {
      showStatus('error', `Kwota doładowania musi mieścić się w przedziale ${MIN_TOP_UP}–${MAX_TOP_UP} zł.`)
      return
    }

    setPreparing(true)
    setMessage('')
    resetPaymentForm()

    try {
      const response = await createWalletTopUpIntent(parsedAmount.toFixed(2), createRequestId())
      setIntent(response)
    } catch (error) {
      showStatus('error', error.message || 'Nie udało się przygotować płatności.')
    } finally {
      setPreparing(false)
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!stripeRef.current || !elementsRef.current || !intent) return

    setSubmitting(true)
    setMessage('')

    try {
      const returnUrl = new URL('/wallet', window.location.origin)
      returnUrl.searchParams.set('topup', 'return')

      const { error, paymentIntent } = await stripeRef.current.confirmPayment({
        elements: elementsRef.current,
        confirmParams: { return_url: returnUrl.toString() },
        redirect: 'if_required',
      })

      if (error) {
        showStatus('error', error.message || 'Stripe odrzucił próbę płatności.')
        return
      }

      const [tone, text] = messageForPaymentStatus(paymentIntent?.status)
      showStatus(tone, text)
      if (paymentIntent?.status === 'succeeded' || paymentIntent?.status === 'processing') {
        onPaymentSettled?.()
      }
    } catch (error) {
      showStatus('error', error.message || 'Nie udało się potwierdzić płatności.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="panel wallet-topup">
      <div className="wallet-topup__heading">
        <div>
          <span className="eyebrow">Doładowanie</span>
          <h2>Dodaj środki do portfela</h2>
          <p>Płatność obsługuje Stripe. doFast zwiększa saldo dopiero po podpisanym potwierdzeniu webhooka.</p>
        </div>
        <span className="wallet-topup__secure">Stripe secure checkout</span>
      </div>

      {!STRIPE_PUBLISHABLE_KEY && (
        <div className="wallet-topup__config-note">
          Integracja jest gotowa, ale lokalnie nie ustawiono jeszcze <code>VITE_STRIPE_PUBLISHABLE_KEY</code>. Po dodaniu klucza testowego pojawi się formularz płatności.
        </div>
      )}

      <div className="wallet-topup__amounts" aria-label="Wybierz kwotę doładowania">
        {PRESET_AMOUNTS.map((preset) => (
          <button
            type="button"
            key={preset}
            className={Number(amount) === preset ? 'wallet-topup__preset wallet-topup__preset--active' : 'wallet-topup__preset'}
            onClick={() => setAmount(preset.toFixed(2))}
            disabled={Boolean(intent) || preparing || submitting}
          >
            {preset} zł
          </button>
        ))}
      </div>

      <label className="wallet-topup__custom-amount">
        <span>Inna kwota</span>
        <div>
          <input
            type="number"
            min={MIN_TOP_UP}
            max={MAX_TOP_UP}
            step="0.01"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            disabled={Boolean(intent) || preparing || submitting}
          />
          <span>PLN</span>
        </div>
      </label>

      {!intent && STRIPE_PUBLISHABLE_KEY && (
        <button type="button" className="wallet-topup__primary" onClick={handlePreparePayment} disabled={preparing}>
          {preparing ? 'Przygotowujemy płatność…' : 'Przejdź do płatności'}
        </button>
      )}

      {intent && (
        <form className="wallet-topup__payment" onSubmit={handleSubmit}>
          <div className="wallet-topup__summary">
            <span>Kwota doładowania</span>
            <strong>{Number(intent.amount).toLocaleString('pl-PL', { style: 'currency', currency: intent.currency || 'PLN' })}</strong>
          </div>
          <div ref={mountRef} className="wallet-topup__stripe-element" />
          <div className="wallet-topup__actions">
            <button type="button" className="wallet-topup__secondary" onClick={resetPaymentForm} disabled={submitting}>
              Zmień kwotę
            </button>
            <button type="submit" className="wallet-topup__primary" disabled={!paymentReady || submitting}>
              {submitting ? 'Potwierdzamy…' : 'Zapłać bezpiecznie'}
            </button>
          </div>
        </form>
      )}

      {message && <div className={`wallet-topup__message wallet-topup__message--${messageTone}`}>{message}</div>}
      <small className="wallet-topup__footnote">Dane karty trafiają bezpośrednio do Stripe i nie przechodzą przez serwer doFast.</small>
    </section>
  )
}

export default StripeTopUpPanel
