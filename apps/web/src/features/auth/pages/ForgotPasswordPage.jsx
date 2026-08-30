import { useState } from 'react'
import { Link } from 'react-router-dom'
import { requestPasswordReset } from '../api/authApi.js'
import './AuthPage.css'

const GENERIC_SUCCESS = 'Jeśli istnieje konto z tym adresem i ma logowanie hasłem, wysłaliśmy instrukcję resetu.'

function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    setSuccess('')
    try {
      await requestPasswordReset(email)
      setSuccess(GENERIC_SUCCESS)
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wysłać prośby o reset hasła.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-card__intro">
          <span className="eyebrow">Odzyskiwanie konta</span>
          <h1>Nie pamiętasz hasła?</h1>
          <p>Podaj adres email konta. Jeśli obsługuje logowanie hasłem, wyślemy jednorazowy link do ustawienia nowego hasła.</p>
        </div>

        <form className="form-stack" onSubmit={submit}>
          <label className="field">
            <span>Email</span>
            <input
              name="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
            />
          </label>
          {success && <div className="form-message" role="status">{success}</div>}
          {error && <div className="form-message form-message--error" role="alert">{error}</div>}
          <button className="button button--primary" type="submit" disabled={submitting}>
            {submitting ? 'Wysyłanie…' : 'Wyślij link resetujący'}
          </button>
        </form>

        <p className="auth-card__switch"><Link to="/login">Wróć do logowania</Link></p>
      </section>
    </main>
  )
}

export default ForgotPasswordPage
