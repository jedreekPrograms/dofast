import { useCallback, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AppleSignInButton from '../components/AppleSignInButton.jsx'
import GoogleSignInButton from '../components/GoogleSignInButton.jsx'
import { useAuth } from '../AuthContext.js'
import './AuthPage.css'

const GOOGLE_ENABLED = Boolean(import.meta.env.VITE_GOOGLE_AUTH_CLIENT_ID?.trim())
const APPLE_ENABLED = Boolean(
  import.meta.env.VITE_APPLE_AUTH_CLIENT_ID?.trim()
  && import.meta.env.VITE_APPLE_AUTH_REDIRECT_URI?.trim()
)

function RegisterPage() {
  const { register, loginWithApple, loginWithGoogle } = useAuth()
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

  const handleGoogleCredential = useCallback(async (credential) => {
    setSubmitting(true)
    setError('')
    try {
      await loginWithGoogle(credential)
      navigate('/my-jobs', { replace: true })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się utworzyć konta przez Google.')
    } finally {
      setSubmitting(false)
    }
  }, [loginWithGoogle, navigate])

  const handleAppleAuthorization = useCallback(async (payload) => {
    setSubmitting(true)
    setError('')
    try {
      await loginWithApple(payload)
      navigate('/my-jobs', { replace: true })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się utworzyć konta przez Apple.')
      throw requestError
    } finally {
      setSubmitting(false)
    }
  }, [loginWithApple, navigate])

  const federatedEnabled = GOOGLE_ENABLED || APPLE_ENABLED

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-card__intro">
          <span className="eyebrow">Nowe konto</span>
          <h1>Dołącz do doFast</h1>
          <p>Jedno konto pozwala zarówno zlecać zadania, jak i przyjmować zlecenia w Twojej okolicy.</p>
        </div>

        {federatedEnabled && (
          <>
            <div className="auth-card__federated">
              {GOOGLE_ENABLED && <GoogleSignInButton onCredential={handleGoogleCredential} disabled={submitting} />}
              {APPLE_ENABLED && <AppleSignInButton onAuthorization={handleAppleAuthorization} disabled={submitting} />}
            </div>
            <div className="auth-card__divider"><span>lub</span></div>
          </>
        )}

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
          {error && <div className="form-message form-message--error" role="alert">{error}</div>}
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