import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { resendEmailVerification, verifyEmail } from '../api/authApi.js'

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''
  const [status, setStatus] = useState(token ? 'verifying' : 'idle')
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)

  useEffect(() => {
    if (!token) return
    let active = true
    verifyEmail(token)
      .then(() => {
        if (!active) return
        setStatus('verified')
        setMessage('Adres email został potwierdzony. Możesz się teraz zalogować.')
      })
      .catch(() => {
        if (!active) return
        setStatus('error')
        setMessage('Link weryfikacyjny jest nieprawidłowy lub wygasł. Poproś o nowy link.')
      })
    return () => { active = false }
  }, [token])

  async function handleResend(event) {
    event.preventDefault()
    if (!email.trim()) return
    setSending(true)
    setMessage('')
    try {
      await resendEmailVerification(email.trim())
      setMessage('Jeśli konto wymaga weryfikacji, nowy link został wysłany.')
    } catch {
      setMessage('Nie udało się wysłać żądania. Spróbuj ponownie później.')
    } finally {
      setSending(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <h1>Weryfikacja email</h1>
        {status === 'verifying' && <p>Sprawdzamy link weryfikacyjny…</p>}
        {message && <p>{message}</p>}
        {status === 'verified' ? (
          <Link to="/login">Przejdź do logowania</Link>
        ) : (
          <form onSubmit={handleResend}>
            <label htmlFor="verification-email">Email</label>
            <input
              id="verification-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              maxLength={320}
              required
            />
            <button type="submit" disabled={sending}>
              {sending ? 'Wysyłanie…' : 'Wyślij nowy link'}
            </button>
          </form>
        )}
      </section>
    </main>
  )
}
