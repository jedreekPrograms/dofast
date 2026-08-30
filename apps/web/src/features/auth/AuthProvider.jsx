import { useEffect, useState } from 'react'
import { clearAccessToken, setAccessToken } from '../../shared/api/apiClient.js'
import {
  changeCurrentUserPassword,
  loginUser,
  loginUserWithApple,
  loginUserWithGoogle,
  logoutUserSession,
  registerUser,
  restoreUserSession,
  updateCurrentUser,
} from './api/authApi.js'
import { AuthContext } from './AuthContext.js'

function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let active = true

    restoreUserSession()
      .then((response) => {
        if (!active || !response) return
        setAccessToken(response.accessToken)
        setUser(response.user)
      })
      .catch(() => {
        clearAccessToken()
        if (active) setUser(null)
      })
      .finally(() => {
        if (active) setReady(true)
      })

    return () => {
      active = false
    }
  }, [])

  function applyAuthResponse(response) {
    setAccessToken(response.accessToken)
    setUser(response.user)
    setReady(true)
    return response.user
  }

  async function login(credentials) {
    return applyAuthResponse(await loginUser(credentials))
  }

  async function loginWithGoogle(credential) {
    return applyAuthResponse(await loginUserWithGoogle(credential))
  }

  async function loginWithApple(payload) {
    return applyAuthResponse(await loginUserWithApple(payload))
  }

  async function register(payload) {
    await registerUser(payload)
    return login({ email: payload.email, password: payload.password })
  }

  async function logout() {
    try {
      await logoutUserSession()
    } catch (error) {
      void error
    } finally {
      clearAccessToken()
      setUser(null)
      setReady(true)
    }
  }

  async function updateProfile(payload) {
    const updated = await updateCurrentUser(payload)
    setUser(updated)
    return updated
  }

  async function changePassword(payload) {
    await changeCurrentUserPassword(payload)
    await logout()
  }

  return (
    <AuthContext.Provider value={{
      user,
      ready,
      login,
      loginWithGoogle,
      loginWithApple,
      register,
      logout,
      updateProfile,
      changePassword,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export default AuthProvider
