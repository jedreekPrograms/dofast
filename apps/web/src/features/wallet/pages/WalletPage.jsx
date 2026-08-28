import { useCallback, useEffect, useRef, useState } from 'react'
import { getWallet, getWalletTransactions } from '../api/walletApi.js'
import StripeTopUpPanel from '../components/StripeTopUpPanel.jsx'
import './WalletPage.css'

const TYPE_LABELS = {
  TOP_UP: 'Wpłata',
  ESCROW_LOCK: 'Blokada środków',
  ESCROW_ADJUSTMENT_LOCK: 'Dopłata do escrow',
  ESCROW_ADJUSTMENT_REFUND: 'Zwrot różnicy escrow',
  ESCROW_RELEASE: 'Wypłata za zlecenie',
  WITHDRAW: 'Wypłata z portfela',
  REFUND: 'Zwrot środków',
}

const moneyFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

function WalletPage() {
  const [wallet, setWallet] = useState(null)
  const [transactions, setTransactions] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const refreshTimersRef = useRef([])

  const loadWallet = useCallback(async ({ initial = false } = {}) => {
    if (initial) setLoading(true)
    else setRefreshing(true)
    setError('')

    try {
      const [walletData, history] = await Promise.all([getWallet(), getWalletTransactions()])
      setWallet(walletData)
      setTransactions(history)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać portfela.')
    } finally {
      if (initial) setLoading(false)
      else setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    loadWallet({ initial: true })
    return () => {
      refreshTimersRef.current.forEach((timer) => window.clearTimeout(timer))
      refreshTimersRef.current = []
    }
  }, [loadWallet])

  const handlePaymentSettled = useCallback(() => {
    refreshTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    refreshTimersRef.current = []

    const refreshDelays = [0, 1500, 4000, 8000]
    refreshDelays.forEach((delay) => {
      const timer = window.setTimeout(() => loadWallet(), delay)
      refreshTimersRef.current.push(timer)
    })
  }, [loadWallet])

  return (
    <main className="wallet-page">
      <header className="page-heading wallet-page__heading">
        <div>
          <span className="eyebrow">Rozliczenia</span>
          <h1>Portfel</h1>
          <p>Saldo, doładowania i pełna historia operacji związanych z Twoimi zleceniami.</p>
        </div>
        <button type="button" className="wallet-refresh" onClick={() => loadWallet()} disabled={loading || refreshing}>
          {refreshing ? 'Odświeżanie…' : 'Odśwież saldo'}
        </button>
      </header>

      {loading && <div className="page-state">Pobieranie portfela…</div>}
      {error && <div className="form-message form-message--error">{error}</div>}
      {!loading && wallet && (
        <>
          <section className="wallet-balance panel">
            <div>
              <span>Dostępne saldo</span>
              <strong>{moneyFormatter.format(Number(wallet.balance))}</strong>
            </div>
            <small>Każda zmiana salda jest zapisywana w ledgerze. Środki z płatności pojawiają się dopiero po potwierdzeniu Stripe.</small>
          </section>

          <StripeTopUpPanel onPaymentSettled={handlePaymentSettled} />

          <section className="panel">
            <div className="wallet-history__heading">
              <div>
                <span className="eyebrow">Ledger</span>
                <h2>Historia</h2>
              </div>
              <span>{transactions.length} operacji</span>
            </div>
            {transactions.length === 0 && <div className="page-state">Brak operacji na portfelu.</div>}
            {transactions.length > 0 && (
              <div className="wallet-history">
                {transactions.map((transaction, index) => (
                  <div className="wallet-transaction" key={`${transaction.createdAt}-${index}`}>
                    <div>
                      <strong>{TYPE_LABELS[transaction.type] || transaction.type}</strong>
                      <span>{transaction.jobId ? `Zlecenie #${transaction.jobId}` : 'Operacja portfela'}</span>
                      <span>{new Date(transaction.createdAt).toLocaleString('pl-PL')}</span>
                    </div>
                    <div className="wallet-transaction__values">
                      <strong>{moneyFormatter.format(Number(transaction.amount))}</strong>
                      <span>Saldo: {moneyFormatter.format(Number(transaction.balanceAfter))}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </main>
  )
}

export default WalletPage
