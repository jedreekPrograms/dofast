import { useEffect, useState } from 'react'
import { getWallet, getWalletTransactions } from '../api/walletApi.js'
import './WalletPage.css'

const TYPE_LABELS = {
  TOP_UP: 'Wpłata',
  ESCROW_LOCK: 'Blokada środków',
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
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([getWallet(), getWalletTransactions()])
      .then(([walletData, history]) => {
        if (!active) return
        setWallet(walletData)
        setTransactions(history)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać portfela.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  return (
    <main className="wallet-page">
      <header className="page-heading">
        <span className="eyebrow">Rozliczenia</span>
        <h1>Portfel</h1>
        <p>Saldo i pełna historia operacji związanych z Twoimi zleceniami.</p>
      </header>

      {loading && <div className="page-state">Pobieranie portfela…</div>}
      {error && <div className="form-message form-message--error">{error}</div>}
      {!loading && wallet && (
        <>
          <section className="wallet-balance panel">
            <span>Dostępne saldo</span>
            <strong>{moneyFormatter.format(Number(wallet.balance))}</strong>
            <small>Każda zmiana salda jest zapisywana w historii. Wpłaty kartą pojawią się w interfejsie po konfiguracji produkcyjnych płatności.</small>
          </section>
          <section className="panel">
            <h2>Historia</h2>
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
