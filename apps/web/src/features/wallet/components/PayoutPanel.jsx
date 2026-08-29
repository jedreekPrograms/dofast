import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  cancelPayout,
  createPayoutOnboardingLink,
  getPayoutEligibility,
  getPayouts,
  refreshPayoutOnboardingStatus,
  requestPayout,
} from '../api/walletApi.js'
import './PayoutPanel.css'

const moneyFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

const STATUS_LABELS = {
  REQUESTED: 'Oczekuje',
  PROCESSING: 'Przetwarzanie',
  REVIEW_REQUIRED: 'Wymaga weryfikacji',
  PAID: 'Wypłacono',
  FAILED: 'Nieudana',
  CANCELLED: 'Anulowana',
}

function createRequestId() {
  if (globalThis.crypto?.randomUUID) return `web-${globalThis.crypto.randomUUID()}`
  return `web-${Date.now()}-${Math.random().toString(36).slice(2, 14)}`
}

function PayoutPanel({ onWalletChanged }) {
  const [eligibility, setEligibility] = useState(null)
  const [payouts, setPayouts] = useState([])
  const [amount, setAmount] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      const [eligibilityData, history] = await Promise.all([
        getPayoutEligibility(),
        getPayouts(),
      ])
      setEligibility(eligibilityData)
      setPayouts(history)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać informacji o wypłatach.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const returnedFromConnect = new URLSearchParams(globalThis.location?.search || '').get('stripe-connect') === 'return'
    if (!returnedFromConnect) {
      load()
      return
    }
    refreshPayoutOnboardingStatus()
      .catch(() => undefined)
      .finally(load)
  }, [load])

  const handleRefresh = async () => {
    setSubmitting(true)
    setError('')
    try {
      if (eligibility?.recipientSetupAvailable) {
        await refreshPayoutOnboardingStatus()
      }
      await load()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się odświeżyć statusu wypłat.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleOnboarding = async () => {
    setSubmitting(true)
    setError('')
    setMessage('')
    try {
      const response = await createPayoutOnboardingLink()
      if (!response?.url) throw new Error('Stripe nie zwrócił adresu onboardingu.')
      globalThis.location.assign(response.url)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się rozpocząć konfiguracji wypłat.')
      setSubmitting(false)
    }
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const numericAmount = Number(amount)
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setError('Podaj poprawną kwotę wypłaty.')
      return
    }

    setSubmitting(true)
    setError('')
    setMessage('')
    try {
      await requestPayout(numericAmount.toFixed(2), createRequestId())
      setAmount('')
      setMessage('Środki zostały zarezerwowane do wypłaty.')
      await Promise.all([load(), onWalletChanged?.()])
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zlecić wypłaty.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleCancel = async (payoutId) => {
    setSubmitting(true)
    setError('')
    setMessage('')
    try {
      await cancelPayout(payoutId)
      setMessage('Wypłata została anulowana, a środki wróciły do portfela.')
      await Promise.all([load(), onWalletChanged?.()])
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się anulować wypłaty.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <section className="panel wallet-payout"><div className="page-state">Pobieranie wypłat…</div></section>
  }

  const availableBalance = Number(eligibility?.availableBalance ?? 0)
  const minimumAmount = Number(eligibility?.minimumAmount ?? 0)
  const isSandbox = eligibility?.providerMode === 'SANDBOX'

  return (
    <section className="panel wallet-payout">
      <div className="wallet-payout__heading">
        <div>
          <span className="eyebrow">Cash-out</span>
          <h2>Wypłać środki</h2>
          <p>
            Zarezerwowana kwota znika z dostępnego salda od razu. Jeśli wypłata zostanie jednoznacznie odrzucona
            albo anulowana przed przetwarzaniem, środki automatycznie wrócą do portfela.
          </p>
        </div>
        <button type="button" className="wallet-refresh" onClick={handleRefresh} disabled={submitting}>
          Odśwież
        </button>
      </div>

      {isSandbox && (
        <div className="wallet-payout__notice wallet-payout__notice--sandbox">
          Tryb sandbox — ta konfiguracja służy wyłącznie do testów i nie wykonuje prawdziwego przelewu bankowego.
        </div>
      )}

      {!eligibility?.identityVerified && (
        <div className="wallet-payout__notice">
          Wypłaty wymagają zweryfikowanej tożsamości. <Link to="/verification">Przejdź do weryfikacji</Link>.
        </div>
      )}

      {eligibility?.identityVerified && eligibility?.recipientSetupAvailable && !eligibility?.recipientReady && (
        <div className="wallet-payout__notice">
          Skonfiguruj konto odbiorcy w Stripe Connect, zanim prawdziwe wypłaty zostaną włączone.
          {' '}
          <button type="button" onClick={handleOnboarding} disabled={submitting}>Skonfiguruj wypłaty</button>
        </div>
      )}

      {eligibility?.identityVerified && !eligibility?.providerAvailable && (
        <div className="wallet-payout__notice">
          Wypłaty są obecnie wyłączone przez operatora platformy. Twoje środki pozostają dostępne w portfelu.
        </div>
      )}

      {error && <div className="form-message form-message--error">{error}</div>}
      {message && <div className="form-message form-message--success">{message}</div>}

      <div className="wallet-payout__summary">
        <div>
          <span>Dostępne do wypłaty</span>
          <strong>{moneyFormatter.format(availableBalance)}</strong>
        </div>
        <small>Minimum: {moneyFormatter.format(minimumAmount)}</small>
      </div>

      <form className="wallet-payout__form" onSubmit={handleSubmit}>
        <label>
          Kwota wypłaty
          <div>
            <input
              type="number"
              min={minimumAmount || 0.01}
              max={availableBalance || undefined}
              step="0.01"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="0,00"
              disabled={!eligibility?.eligible || submitting}
            />
            <span>PLN</span>
          </div>
        </label>
        <button type="submit" className="wallet-topup__primary" disabled={!eligibility?.eligible || submitting}>
          {submitting ? 'Przetwarzanie…' : 'Zleć wypłatę'}
        </button>
      </form>

      <div className="wallet-payout__history">
        <div className="wallet-history__heading">
          <div>
            <span className="eyebrow">Historia wypłat</span>
            <h2>Statusy</h2>
          </div>
          <span>{payouts.length} operacji</span>
        </div>

        {payouts.length === 0 && <div className="page-state">Nie zlecałeś jeszcze żadnej wypłaty.</div>}
        {payouts.map((payout) => (
          <div className="wallet-payout__row" key={payout.id}>
            <div>
              <strong>{moneyFormatter.format(Number(payout.amount))}</strong>
              <span>{new Date(payout.requestedAt).toLocaleString('pl-PL')}</span>
              <span>Próby providera: {payout.attemptCount}</span>
            </div>
            <div className="wallet-payout__row-status">
              <span className={`wallet-payout__status wallet-payout__status--${String(payout.status).toLowerCase()}`}>
                {STATUS_LABELS[payout.status] || payout.status}
              </span>
              {payout.cancellable && (
                <button type="button" onClick={() => handleCancel(payout.id)} disabled={submitting}>
                  Anuluj
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

export default PayoutPanel
