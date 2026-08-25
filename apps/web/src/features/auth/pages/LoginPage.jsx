import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../AuthContext.js'
import './AuthPage.css'

function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await login(form)
      const destination = location.state?.from?.pathname || '/my-jobs'
      navigate(destination, { replace: true })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zalogować.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-card__intro">
          <span className="eyebrow">Twoje konto</span>
          <h1>Zaloguj się do doFast</h1>
          <p>Zarządzaj zleceniami, rozmawiaj z wykonawcami i kontroluj rozliczenia z jednego miejsca.</p>
        </div>
        <form className="form-stack" onSubmit={submit}>
          <label className="field">
            <span>Email</span>
            <input name="email" type="email" value={form.email} onChange={updateField} autoComplete="email" required />
          </label>
          <label className="field">
            <span>Hasło</span>
            <input name="password" type="password" value={form.password} onChange={updateField} autoComplete="current-password" required />
          </label>
          {error && <div className="form-message form-message--error">{error}</div>}
          <button className="button button--primary" type="submit" disabled={submitting}>
            {submitting ? 'Logowanie…' : 'Zaloguj się'}
          </button>
        </form>
        <p className="auth-card__switch">Nie masz konta? <Link to="/register">Zarejestruj się</Link></p>
      </section>
    </main>
  )
}

export default LoginPage
