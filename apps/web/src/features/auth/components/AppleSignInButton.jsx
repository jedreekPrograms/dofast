import { useCallback, useEffect, useRef, useState } from 'react'
import { createAppleLoginChallenge } from '../api/authApi.js'

const APPLE_SCRIPT_URL = 'https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js'
const APPLE_CLIENT_ID = import.meta.env.VITE_APPLE_AUTH_CLIENT_ID?.trim()
const APPLE_REDIRECT_URI = import.meta.env.VITE_APPLE_AUTH_REDIRECT_URI?.trim()

function AppleSignInButton({ onAuthorization, disabled = false }) {
  const [scriptReady, setScriptReady] = useState(Boolean(window.AppleID?.auth))
  const [challenge, setChallenge] = useState(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const preparingRef = useRef(false)

  const prepareChallenge = useCallback(async () => {
    if (preparingRef.current || !APPLE_CLIENT_ID || !APPLE_REDIRECT_URI) return
    preparingRef.current = true
    try {
      setError('')
      setChallenge(await createAppleLoginChallenge())
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się przygotować logowania Apple.')
    } finally {
      preparingRef.current = false
    }
  }, [])

  useEffect(() => {
    if (!APPLE_CLIENT_ID || !APPLE_REDIRECT_URI || window.AppleID?.auth) return undefined

    let active = true
    let script = document.querySelector('script[data-dofast-apple-sign-in]')
    const handleLoad = () => {
      if (active) setScriptReady(Boolean(window.AppleID?.auth))
    }
    const handleError = () => {
      if (active) setError('Nie udało się załadować logowania Apple.')
    }

    if (!script) {
      script = document.createElement('script')
      script.src = APPLE_SCRIPT_URL
      script.async = true
      script.defer = true
      script.dataset.dofastAppleSignIn = 'true'
      document.head.appendChild(script)
    }

    script.addEventListener('load', handleLoad)
    script.addEventListener('error', handleError)
    return () => {
      active = false
      script.removeEventListener('load', handleLoad)
      script.removeEventListener('error', handleError)
    }
  }, [])

  useEffect(() => {
    if (scriptReady && !challenge) prepareChallenge()
  }, [challenge, prepareChallenge, scriptReady])

  useEffect(() => {
    if (!scriptReady || !challenge || !window.AppleID?.auth) return undefined

    window.AppleID.auth.init({
      clientId: APPLE_CLIENT_ID,
      scope: 'name email',
      redirectURI: APPLE_REDIRECT_URI,
      state: challenge.state,
      nonce: challenge.nonce,
      usePopup: true,
    })

    async function handleSuccess(event) {
      const result = event.detail
      const authorization = result?.authorization
      setSubmitting(true)
      setError('')
      try {
        if (!authorization?.code || authorization.state !== challenge.state) {
          throw new Error('Apple authorization response is incomplete')
        }
        await onAuthorization({
          challengeId: challenge.challengeId,
          code: authorization.code,
          state: authorization.state,
          nonce: challenge.nonce,
          firstName: result?.user?.name?.firstName || null,
          lastName: result?.user?.name?.lastName || null,
        })
      } catch (requestError) {
        setError(requestError.message || 'Nie udało się zalogować przez Apple.')
        setChallenge(null)
      } finally {
        setSubmitting(false)
      }
    }

    function handleFailure(event) {
      const code = event.detail?.error
      if (code !== 'popup_closed_by_user') {
        setError('Logowanie Apple nie powiodło się.')
      }
      setChallenge(null)
    }

    document.addEventListener('AppleIDSignInOnSuccess', handleSuccess)
    document.addEventListener('AppleIDSignInOnFailure', handleFailure)
    return () => {
      document.removeEventListener('AppleIDSignInOnSuccess', handleSuccess)
      document.removeEventListener('AppleIDSignInOnFailure', handleFailure)
    }
  }, [challenge, onAuthorization, scriptReady])

  if (!APPLE_CLIENT_ID || !APPLE_REDIRECT_URI) return null

  return (
    <div className={`apple-sign-in${disabled || submitting || !challenge ? ' apple-sign-in--disabled' : ''}`}>
      <div
        id="appleid-signin"
        data-color="black"
        data-border="true"
        data-type="continue"
        data-mode="center-align"
      />
      {error && <small role="alert">{error}</small>}
    </div>
  )
}

export default AppleSignInButton