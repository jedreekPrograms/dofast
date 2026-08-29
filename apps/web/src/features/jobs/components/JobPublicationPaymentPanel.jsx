import { useCallback, useEffect, useRef, useState } from 'react'
import { loadStripeScript, STRIPE_PUBLISHABLE_KEY } from '../../../shared/payments/stripeClient.js'
import {
  cancelJobPublication,
  createJobPublicationPaymentIntent,
  getJobPublication,
} from '../api/jobsApi.js'

const POLL_INTERVAL_MS = 1000
const MAX_POLL_ATTEMPTS = 45

function money(value, currency = 'PLN') {
  return Number(value || 0).toLocaleString('pl-PL', { style: 'currency', currency })
}

function JobPublicationPaymentPanel({ publication, onPublished, onReset }) {
  const [current, setCurrent] = useState(publication)
  const [intent, setIntent] = useState(null)
  const [preparing, setPreparing] = useState(Boolean(STRIPE_PUBLISHABLE_KEY))
  const [paymentReady, setPaymentReady] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [checking, setChecking] = useState(false)
  const [message, setMessage] = useState('')
  const [messageTone, setMessageTone] = useState('info')

  const mountRef = useRef(null)
  const stripeRef = useRef(null)
  const elementsRef = useRef(null)
  const paymentElementRef = useRef(null)

  const showMessage = useCallback((tone, text) => {
    setMessageTone(tone)
    setMessage(text)
  }, [])

  const refreshPublication = useCallback(async () => {
    const latest = await getJobPublication(publication.id)
    setCurrent(latest)
    if (latest.status === 'PUBLISHED' && latest.jobId) {
      onPublished(latest.jobId)
    }
    return latest
  }, [onPublished, publication.id])

  const pollUntilTerminal = useCallback(async () => {
    setChecking(true)
    try {
      for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt += 1) {
        const latest = await refreshPublication()
        if (latest.status === 'PUBLISHED') return
        if (latest.status === 'PAYMENT_RECEIVED') {
          showMessage('info', 'Płatność została zaksięgowana w portfelu, ale zlecenie nie mogło już zostać opublikowane. Wróć do formularza i opublikuj je ponownie z dostępnego salda.')
          return
        }
        if (latest.status === 'CANCELLED') {
          showMessage('info', 'Publikacja została anulowana. Zarezerwowane środki z portfela zostały zwolnione.')
          return
        }
        await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))
      }
      showMessage('info', 'Płatność została przekazana do Stripe. Publikację nadal potwierdzi podpisany webhook; możesz też odświeżyć jej status.')
    } catch (error) {
      showMessage('error', error.message || 'Nie udało się sprawdzić stanu publikacji.')
    } finally {
      setChecking(false)
    }
  }, [refreshPublication, showMessage])

  useEffect(() => {
    if (!STRIPE_PUBLISHABLE_KEY || current.status !== 'PAYMENT_REQUIRED') {
      setPreparing(false)
      return undefined
    }

    let active = true
    setPreparing(true)
    createJobPublicationPaymentIntent(publication.id)
      .then((response) => {
        if (active) setIntent(response)
      })
      .catch((error) => {
        if (active) showMessage('error', error.message || 'Nie udało się przygotować płatności za publikację.')
      })
      .finally(() => {
        if (active) setPreparing(false)
      })

    return () => { active = false }
  }, [current.status, publication.id, showMessage])

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
        if (active) showMessage('error', error.message || 'Nie udało się uruchomić formularza Stripe.')
      })

    return () => {
      active = false
      paymentElementRef.current?.unmount()
      paymentElementRef.current = null
      elementsRef.current = null
      stripeRef.current = null
    }
  }, [intent?.clientSecret, showMessage])

  async function pay(event) {
    event.preventDefault()
    if (!stripeRef.current || !elementsRef.current) return

    setSubmitting(true)
    setMessage('')
    try {
      const returnUrl = new URL('/jobs/new', window.location.origin)
      const { error, paymentIntent } = await stripeRef.current.confirmPayment({
        elements: elementsRef.current,
        confirmParams: { return_url: returnUrl.toString() },
        redirect: 'if_required',
      })
      if (error) {
        showMessage('error', error.message || 'Stripe odrzucił próbę płatności.')
        return
      }
      if (paymentIntent?.status === 'succeeded' || paymentIntent?.status === 'processing') {
        showMessage('success', 'Płatność przyjęta przez Stripe. Czekamy na podpisane potwierdzenie serwera i publikację zlecenia…')
        await pollUntilTerminal()
      } else {
        showMessage('info', 'Płatność nie ma jeszcze końcowego statusu. Możesz spróbować ponownie lub odświeżyć stan publikacji.')
      }
    } catch (error) {
      showMessage('error', error.message || 'Nie udało się potwierdzić płatności.')
    } finally {
      setSubmitting(false)
    }
  }

  async function cancel() {
    setCancelling(true)
    setMessage('')
    try {
      const result = await cancelJobPublication(publication.id)
      setCurrent(result)
      onReset('Publikacja została anulowana. Zarezerwowane środki wróciły do dostępnego salda.')
    } catch (error) {
      showMessage('error', error.message || 'Nie udało się anulować publikacji.')
    } finally {
      setCancelling(false)
    }
  }

  const surplus = Math.max(0, Number(current.paymentAmount || 0) - Number(current.missingAmount || 0))
  const terminalWithoutJob = current.status === 'PAYMENT_RECEIVED' || current.status === 'CANCELLED'

  return (
    <section className="panel job-publication-payment" aria-live="polite">
      <div className="job-publication-payment__heading">
        <div>
          <span className="eyebrow">Finansowanie zlecenia</span>
          <h2>Uzupełnij brakującą kwotę</h2>
          <p>Zlecenie stanie się publiczne dopiero po potwierdzeniu pełnego escrow przez backend.</p>
        </div>
        <span className="job-publication-payment__secure">Stripe secure checkout</span>
      </div>

      <div className="job-publication-payment__summary">
        <div><span>Łącznie do zabezpieczenia</span><strong>{money(current.totalAmount, current.currency)}</strong></div>
        <div><span>Zarezerwowane z portfela</span><strong>{money(current.walletReservedAmount, current.currency)}</strong></div>
        <div><span>Brakuje do escrow</span><strong>{money(current.missingAmount, current.currency)}</strong></div>
        <div className="job-publication-payment__charge"><span>Płatność Stripe</span><strong>{money(current.paymentAmount, current.currency)}</strong></div>
      </div>

      <div className="job-publication-payment__note">
        Łączna kwota obejmuje wynagrodzenie za usługę oraz ewentualny budżet zakupowy. Prowizja platformy jest rozliczana tylko od wynagrodzenia; zwrot udokumentowanych wydatków jest od niej oddzielony.
      </div>

      {surplus > 0 && (
        <div className="job-publication-payment__note">
          Minimalna płatność online to 1,00 zł. Nadwyżka {money(surplus, current.currency)} pozostanie w Twoim portfelu po rozliczeniu.
        </div>
      )}

      {!STRIPE_PUBLISHABLE_KEY && current.status === 'PAYMENT_REQUIRED' && (
        <>
          <div className="job-publication-payment__note job-publication-payment__note--error">
            Brakuje <code>VITE_STRIPE_PUBLISHABLE_KEY</code>. Publikacja pozostaje prywatna, a środki z portfela są tylko zarezerwowane do czasu anulowania lub wygaśnięcia.
          </div>
          <div className="job-publication-payment__actions">
            <button type="button" className="button button--secondary" onClick={cancel} disabled={cancelling || checking}>
              {cancelling ? 'Anulowanie…' : 'Anuluj i zwolnij środki'}
            </button>
          </div>
        </>
      )}

      {current.status === 'PAYMENT_REQUIRED' && STRIPE_PUBLISHABLE_KEY && (
        <form className="job-publication-payment__form" onSubmit={pay}>
          {preparing && <div className="job-publication-payment__note">Przygotowujemy bezpieczną płatność…</div>}
          <div ref={mountRef} className="job-publication-payment__stripe" />
          <div className="job-publication-payment__actions">
            <button type="button" className="button button--secondary" onClick={cancel} disabled={submitting || cancelling || checking}>
              {cancelling ? 'Anulowanie…' : 'Anuluj i zwolnij środki'}
            </button>
            <button type="submit" className="button button--primary" disabled={!paymentReady || submitting || cancelling || checking}>
              {submitting || checking ? 'Potwierdzamy publikację…' : `Zapłać ${money(current.paymentAmount, current.currency)}`}
            </button>
          </div>
        </form>
      )}

      {message && <div className={`job-publication-payment__message job-publication-payment__message--${messageTone}`}>{message}</div>}

      {current.status === 'PAYMENT_REQUIRED' && (
        <button type="button" className="job-publication-payment__refresh" onClick={refreshPublication} disabled={checking}>
          Odśwież stan publikacji
        </button>
      )}

      {terminalWithoutJob && (
        <button type="button" className="button button--primary" onClick={() => onReset('Możesz ponownie przygotować publikację z aktualnym saldem.')}>
          Wróć do formularza
        </button>
      )}

      <small className="job-publication-payment__footnote">
        Dane metody płatności trafiają bezpośrednio do Stripe. doFast publikuje zlecenie wyłącznie po rozliczeniu po stronie serwera.
      </small>
    </section>
  )
}

export default JobPublicationPaymentPanel
