import { useCallback, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import AppleSignInButton from '../components/AppleSignInButton.jsx'
import GoogleSignInButton from '../components/GoogleSignInButton.jsx'
import { useAuth } from '../AuthContext.js'
import './AuthPage.css'

const GOOGLE_ENABLED = Boolean(import.meta.env.VITE_GOOGLE_AUTH_CLIENT_ID?.trim())
const APPLE_ENABLED = Boolean(
  import.meta.env.VITE_APPLE_AUTH_CLIENT_ID?.trim()
  && import.meta.env.VITE_APPLE_AUTH_REDIRECT_URI?.trim()
)

function LoginPage() {
  const { login, loginWithApple, loginWithGoogle } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const destination = location.state?.from?.pathname || '/my-jobs'

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
      navigate(destination, { replace: true })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zalogować.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleGoogleCredential = useCallback(async (credential) => {
    setSubmitting(true)
    setError('')
    try {
      await loginWithGoogle(credential)
      navigate(destination, { replace: true })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zalogować przez Google.')
    } finally {
      setSubmitting(false)
    }
  }, [destination, loginWithGoogle, navigate])

  const handleAppleAuthorization = useCallback(async (payload) => {
    setSubmitting(true)
    setError('')
    try {
      await loginWithApple(payload)
      navigate(destination, { replace: true })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zalogować przez Apple.')
      throw requestError
    } finally {
      setSubmitting(false)
    }
  }, [destination, loginWithApple, navigate])

  const federatedEnabled = GOOGLE_ENABLED || APPLE_ENABLED

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-card__intro">
          <span className="eyebrow">Twoje konto</span>
          <h1>Zaloguj się do doFast</h1>
          <p>Zarządzaj zleceniami, rozmawiaj z wykonawcami i kontroluj rozliczenia z jednego miejsca.</p>
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
            <input name="email" type="email" value={form.email} onChange={updateField} autoComplete="email" required />
          </label>
          <label className="field">
            <span>Hasło</span>
            <input name="password" type="password" value={form.password} onChange={updateField} autoComplete="current-password" required />
          </label>
          <div className="auth-card__switch"><Link to="/forgot-password">Nie pamiętasz hasła?</Link></div>
          {error && <div className="form-message form-message--error" role="alert">{error}</div>}
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