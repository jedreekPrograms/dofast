import { useEffect, useState } from 'react'
import { getWallet, getWalletTransactions } from '../api/walletApi.js'
import './WalletPage.css'

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
        <p>Saldo i historia operacji związanych z Twoimi zleceniami.</p>
      </header>

      {loading && <div className="page-state">Pobieranie portfela…</div>}
      {error && <div className="form-message form-message--error">{error}</div>}
      {!loading && wallet && (
        <>
          <section className="wallet-balance panel">
            <span>Dostępne saldo</span>
            <strong>{Number(wallet.balance).toLocaleString('pl-PL', { style: 'currency', currency: 'PLN' })}</strong>
            <small>Integrację z realnymi wpłatami uruchomimy dopiero po domknięciu bezpiecznego przepływu płatności.</small>
          </section>
          <section className="panel">
            <h2>Historia</h2>
            {transactions.length === 0 && <div className="page-state">Brak operacji na portfelu.</div>}
            {transactions.length > 0 && (
              <div className="wallet-history">
                {transactions.map((transaction, index) => (
                  <div className="wallet-transaction" key={`${transaction.createdAt}-${index}`}>
                    <div>
                      <strong>{transaction.type}</strong>
                      <span>{transaction.jobId ? `Zlecenie #${transaction.jobId}` : 'Operacja portfela'}</span>
                    </div>
                    <div className="wallet-transaction__amount">
                      {Number(transaction.amount).toLocaleString('pl-PL', { style: 'currency', currency: 'PLN' })}
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
