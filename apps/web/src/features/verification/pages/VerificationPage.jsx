import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyVerification, requestIdentityVerification } from '../api/verificationApi.js'
import './VerificationPage.css'

const statusCopy = {
  NOT_STARTED: {
    title: 'Weryfikacja nie została rozpoczęta',
    body: 'Możesz uruchomić proces weryfikacji tożsamości. Po pozytywnej decyzji na Twoim publicznym profilu pojawi się oznaczenie zweryfikowanej tożsamości.',
    tone: 'neutral',
  },
  PENDING: {
    title: 'Weryfikacja jest w toku',
    body: 'Zgłoszenie oczekuje na zakończenie procesu weryfikacji. Nie musisz wysyłać go ponownie.',
    tone: 'pending',
  },
  VERIFIED: {
    title: 'Tożsamość zweryfikowana',
    body: 'Weryfikacja jest aktywna i jest widoczna jako oznaczenie zaufania na Twoim publicznym profilu.',
    tone: 'verified',
  },
  REJECTED: {
    title: 'Weryfikacja nie została zaakceptowana',
    body: 'Możesz ponownie uruchomić proces po sprawdzeniu informacji o decyzji.',
    tone: 'rejected',
  },
  REVOKED: {
    title: 'Weryfikacja została cofnięta',
    body: 'Oznaczenie zweryfikowanej tożsamości nie jest obecnie aktywne. Możesz zgłosić się ponownie.',
    tone: 'rejected',
  },
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('pl-PL') : '—'
}

function VerificationPage() {
  const [verification, setVerification] = useState(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    getMyVerification()
      .then((response) => {
        if (active) setVerification(response)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać statusu weryfikacji.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  async function submitVerification() {
    setSubmitting(true)
    setError('')
    try {
      setVerification(await requestIdentityVerification())
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się rozpocząć weryfikacji.')
    } finally {
      setSubmitting(false)
    }
  }

  const copy = verification ? statusCopy[verification.status] : null
  const canRequest = verification?.status === 'NOT_STARTED' || verification?.canRequest

  return (
    <main className="verification-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Zaufanie i bezpieczeństwo</span>
          <h1>Weryfikacja tożsamości</h1>
          <p>Kontroluj status weryfikacji i oznaczenie widoczne dla innych użytkowników.</p>
        </div>
        <Link className="button button--secondary" to="/profile">Wróć do profilu</Link>
      </header>

      {loading && <div className="page-state">Pobieranie statusu weryfikacji…</div>}
      {error && <div className="form-message form-message--error">{error}</div>}

      {!loading && verification && copy && (
        <>
          <section className={`panel verification-status verification-status--${copy.tone}`}>
            <div className="verification-status__icon" aria-hidden="true">
              {verification.status === 'VERIFIED' ? '✓' : verification.status === 'PENDING' ? '…' : '!'}
            </div>
            <div className="verification-status__content">
              <span className="eyebrow">Status: {verification.status}</span>
              <h2>{copy.title}</h2>
              <p>{copy.body}</p>
              {verification.decisionReason && (
                <div className="verification-status__reason">
                  <strong>Informacja o decyzji</strong>
                  <span>{verification.decisionReason}</span>
                </div>
              )}
              {canRequest && (
                <button className="button button--primary" type="button" disabled={submitting} onClick={submitVerification}>
                  {submitting ? 'Wysyłanie…' : verification.status === 'NOT_STARTED' ? 'Rozpocznij weryfikację' : 'Zgłoś ponownie'}
                </button>
              )}
            </div>
          </section>

          <section className="verification-grid">
            <article className="panel verification-info">
              <span className="eyebrow">Prywatność</span>
              <h2>Minimalizujemy przechowywane dane</h2>
              <p>doFast przechowuje status procesu, identyfikator techniczny providera (jeśli występuje), decyzję i historię zmian. Ten moduł nie zapisuje kopii dokumentów ani numerów dokumentów tożsamości.</p>
            </article>
            <article className="panel verification-info">
              <span className="eyebrow">Historia procesu</span>
              <dl>
                <div><dt>Zgłoszono</dt><dd>{formatDate(verification.requestedAt)}</dd></div>
                <div><dt>Rozpatrzono</dt><dd>{formatDate(verification.reviewedAt)}</dd></div>
                <div><dt>Zweryfikowano</dt><dd>{formatDate(verification.verifiedAt)}</dd></div>
                <div><dt>Cofnięto</dt><dd>{formatDate(verification.revokedAt)}</dd></div>
              </dl>
            </article>
          </section>
        </>
      )}
    </main>
  )
}

export default VerificationPage
