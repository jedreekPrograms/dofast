import { useEffect, useMemo, useState } from 'react'
import { getPlatformFeeQuote } from '../api/paymentsApi.js'
import './PlatformFeePreview.css'

const moneyFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

function PlatformFeePreview({ amount, jobId = null, compact = false }) {
  const amountKey = useMemo(() => normalizeAmount(amount), [amount])
  const requestKey = amountKey ? `${jobId || 'current'}:${amountKey}` : null
  const [state, setState] = useState({ key: null, quote: null, error: '' })

  useEffect(() => {
    if (!requestKey) return undefined

    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      getPlatformFeeQuote(amountKey, jobId, { signal: controller.signal })
        .then((quote) => setState({ key: requestKey, quote, error: '' }))
        .catch((requestError) => {
          if (requestError.name !== 'AbortError') {
            setState({
              key: requestKey,
              quote: null,
              error: 'Nie udało się pobrać aktualnego podglądu rozliczenia.',
            })
          }
        })
    }, 250)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [amountKey, jobId, requestKey])

  if (!amountKey) {
    return compact ? null : (
      <div className="platform-fee-preview platform-fee-preview--empty">
        Wpisz wynagrodzenie, aby zobaczyć dokładny podział wypłaty.
      </div>
    )
  }

  const current = state.key === requestKey ? state : null
  const quote = current?.quote || null
  const error = current?.error || ''
  const loading = !current

  return (
    <div className={`platform-fee-preview${compact ? ' platform-fee-preview--compact' : ''}`} aria-live="polite">
      <div className="platform-fee-preview__heading">
        <strong>Rozliczenie wykonawcy</strong>
        {quote && <span>{formatPercent(quote.percent)} prowizji doFast</span>}
      </div>

      {loading && <span className="platform-fee-preview__loading">Liczenie dokładnej wypłaty…</span>}
      {error && <span className="platform-fee-preview__error">{error}</span>}

      {quote && (
        <div className="platform-fee-preview__breakdown">
          <div><span>Kwota zlecenia</span><strong>{formatMoney(quote.grossAmount)}</strong></div>
          <div><span>Prowizja doFast</span><strong>− {formatMoney(quote.platformFeeAmount)}</strong></div>
          <div className="platform-fee-preview__net"><span>Wykonawca otrzyma</span><strong>{formatMoney(quote.workerPayoutAmount)}</strong></div>
        </div>
      )}

      {!compact && (
        <small>
          Zlecający finansuje tylko podany budżet. Prowizja jest potrącana z wypłaty wykonawcy dopiero po skutecznym zakończeniu zlecenia; przy zwrocie escrow nie jest pobierana. Stawka zostaje zamrożona przy publikacji.
        </small>
      )}
    </div>
  )
}

function normalizeAmount(value) {
  if (value === '' || value === null || value === undefined) return null
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric < 0.01) return null
  return numeric.toFixed(2)
}

function formatMoney(value) {
  return moneyFormatter.format(Number(value))
}

function formatPercent(value) {
  return `${Number(value).toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`
}

export default PlatformFeePreview
