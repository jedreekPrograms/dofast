import { useEffect, useRef, useState } from 'react'

const GOOGLE_SCRIPT_URL = 'https://accounts.google.com/gsi/client'
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_AUTH_CLIENT_ID?.trim()

function GoogleSignInButton({ onCredential, disabled = false }) {
  const containerRef = useRef(null)
  const callbackRef = useRef(onCredential)
  const [loadError, setLoadError] = useState(false)

  useEffect(() => {
    callbackRef.current = onCredential
  }, [onCredential])

  useEffect(() => {
    if (!GOOGLE_CLIENT_ID || !containerRef.current) return undefined

    let active = true

    function renderButton() {
      if (!active || !containerRef.current || !window.google?.accounts?.id) return

      containerRef.current.replaceChildren()
      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        ux_mode: 'popup',
        callback: (response) => {
          if (response?.credential) callbackRef.current(response.credential)
        },
      })
      window.google.accounts.id.renderButton(containerRef.current, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        text: 'continue_with',
        shape: 'rectangular',
        logo_alignment: 'left',
        width: Math.min(containerRef.current.clientWidth || 400, 400),
      })
    }

    if (window.google?.accounts?.id) {
      renderButton()
      return () => { active = false }
    }

    let script = document.querySelector('script[data-dofast-google-identity]')
    const handleLoad = () => {
      setLoadError(false)
      renderButton()
    }
    const handleError = () => {
      if (active) setLoadError(true)
    }

    if (!script) {
      script = document.createElement('script')
      script.src = GOOGLE_SCRIPT_URL
      script.async = true
      script.defer = true
      script.dataset.dofastGoogleIdentity = 'true'
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

  if (!GOOGLE_CLIENT_ID) return null

  return (
    <div className={`google-sign-in${disabled ? ' google-sign-in--disabled' : ''}`}>
      <div ref={containerRef} aria-hidden={disabled} />
      {loadError && <small>Nie udało się załadować logowania Google. Spróbuj ponownie później.</small>}
    </div>
  )
}

export default GoogleSignInButton