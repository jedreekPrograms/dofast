import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { resetPassword } from '../api/authApi.js'
import './AuthPage.css'

function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')?.trim() || ''
  const [form, setForm] = useState({ password: '', confirmPassword: '' })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    setError('')

    if (!token) {
      setError('Link resetu hasła jest nieprawidłowy lub niekompletny.')
      return
    }
    if (form.password !== form.confirmPassword) {
      setError('Hasła nie są takie same.')
      return
    }

    setSubmitting(true)
    try {
      await resetPassword(token, form.password)
      setSuccess(true)
      setForm({ password: '', confirmPassword: '' })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się ustawić nowego hasła.')
    } finally {
      setSubmitting(false)
    }
  }

  if (success) {
    return (
      <main className="auth-page">
        <section className="auth-card">
          <div className="auth-card__intro">
            <span className="eyebrow">Hasło zmienione</span>
            <h1>Możesz zalogować się ponownie</h1>
            <p>Poprzednie sesje zostały unieważnione. Zaloguj się nowym hasłem na zaufanym urządzeniu.</p>
          </div>
          <Link className="button button--primary" to="/login">Przejdź do logowania</Link>
        </section>
      </main>
    )
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-card__intro">
          <span className="eyebrow">Odzyskiwanie konta</span>
          <h1>Ustaw nowe hasło</h1>
          <p>Link resetujący jest jednorazowy. Po zmianie hasła wszystkie wcześniejsze sesje logowania zostaną unieważnione.</p>
        </div>

        <form className="form-stack" onSubmit={submit}>
          <label className="field">
            <span>Nowe hasło</span>
            <input
              name="password"
              type="password"
              value={form.password}
              onChange={updateField}
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
              required
            />
          </label>
          <label className="field">
            <span>Powtórz nowe hasło</span>
            <input
              name="confirmPassword"
              type="password"
              value={form.confirmPassword}
              onChange={updateField}
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
              required
            />
          </label>
          {!token && (
            <div className="form-message form-message--error" role="alert">
              Link resetu hasła jest nieprawidłowy lub niekompletny.
            </div>
          )}
          {error && <div className="form-message form-message--error" role="alert">{error}</div>}
          <button className="button button--primary" type="submit" disabled={submitting || !token}>
            {submitting ? 'Zapisywanie…' : 'Ustaw nowe hasło'}
          </button>
        </form>

        <p className="auth-card__switch"><Link to="/login">Wróć do logowania</Link></p>
      </section>
    </main>
  )
}

export default ResetPasswordPage
