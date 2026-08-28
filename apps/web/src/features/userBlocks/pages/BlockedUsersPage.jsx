import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyUserBlocks, unblockUser } from '../api/userBlocksApi.js'
import './BlockedUsersPage.css'

function BlockedUsersPage() {
  const [blocks, setBlocks] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [unblockingId, setUnblockingId] = useState(null)

  useEffect(() => {
    let active = true

    getMyUserBlocks()
      .then((data) => {
        if (active) setBlocks(data)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać listy blokad.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [])

  async function handleUnblock(userId) {
    setError('')
    setUnblockingId(userId)
    try {
      await unblockUser(userId)
      setBlocks((current) => current.filter((block) => block.userId !== userId))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się odblokować użytkownika.')
    } finally {
      setUnblockingId(null)
    }
  }

  return (
    <main className="blocked-users-page">
      <header className="page-heading">
        <span className="eyebrow">Prywatność i bezpieczeństwo</span>
        <h1>Zablokowani użytkownicy</h1>
        <p>Zarządzaj kontami, którym zablokowałeś możliwość wysyłania do Ciebie nowych wiadomości.</p>
      </header>

      <div className="blocked-users-page__notice" role="note">
        <strong>Blokada jest prywatna.</strong>
        <span>Nie pokazujemy drugiej stronie, czy ani kiedy została przez Ciebie zablokowana. Dotychczasowa historia rozmów pozostaje dostępna uczestnikom zlecenia.</span>
      </div>

      {error && <div className="form-message form-message--error">{error}</div>}
      {loading && <div className="page-state">Pobieranie listy blokad…</div>}

      {!loading && blocks.length === 0 && (
        <section className="panel blocked-users-empty">
          <h2>Nie masz zablokowanych użytkowników</h2>
          <p>Drugą stronę aktywnego lub historycznego zlecenia możesz zablokować z poziomu rozmowy.</p>
          <Link className="button button--primary" to="/chat">Przejdź do czatów</Link>
        </section>
      )}

      {!loading && blocks.length > 0 && (
        <section className="blocked-users-list" aria-label="Zablokowani użytkownicy">
          {blocks.map((block) => (
            <article className="panel blocked-user-card" key={block.userId}>
              <div>
                <strong>{block.nickname}</strong>
                <span>Zablokowano {new Date(block.blockedAt).toLocaleString('pl-PL')}</span>
              </div>
              <div className="blocked-user-card__actions">
                <Link className="button button--secondary" to={`/users/${block.userId}`}>Profil</Link>
                <button
                  className="button button--secondary"
                  type="button"
                  disabled={unblockingId === block.userId}
                  onClick={() => handleUnblock(block.userId)}
                >
                  {unblockingId === block.userId ? 'Odblokowywanie…' : 'Odblokuj'}
                </button>
              </div>
            </article>
          ))}
        </section>
      )}
    </main>
  )
}

export default BlockedUsersPage
