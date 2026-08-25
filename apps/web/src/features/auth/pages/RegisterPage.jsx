import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../AuthContext.js'
import './AuthPage.css'

function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', nickname: '', password: '' })
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
      await register(form)
      navigate('/my-jobs', { replace: true })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się utworzyć konta.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-card__intro">
          <span className="eyebrow">Nowe konto</span>
          <h1>Dołącz do doFast</h1>
          <p>Jedno konto pozwala zarówno zlecać zadania, jak i przyjmować zlecenia w Twojej okolicy.</p>
        </div>
        <form className="form-stack" onSubmit={submit}>
          <label className="field">
            <span>Email</span>
            <input name="email" type="email" value={form.email} onChange={updateField} autoComplete="email" maxLength={320} required />
          </label>
          <label className="field">
            <span>Nazwa użytkownika</span>
            <input name="nickname" value={form.nickname} onChange={updateField} minLength={3} maxLength={80} autoComplete="nickname" required />
          </label>
          <label className="field">
            <span>Hasło</span>
            <input name="password" type="password" value={form.password} onChange={updateField} minLength={8} maxLength={72} autoComplete="new-password" required />
            <small>Minimum 8 znaków.</small>
          </label>
          {error && <div className="form-message form-message--error">{error}</div>}
          <button className="button button--primary" type="submit" disabled={submitting}>
            {submitting ? 'Tworzenie konta…' : 'Załóż konto'}
          </button>
        </form>
        <p className="auth-card__switch">Masz już konto? <Link to="/login">Zaloguj się</Link></p>
      </section>
    </main>
  )
}

export default RegisterPage
