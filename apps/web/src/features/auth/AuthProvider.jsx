import { useEffect, useState } from 'react'
import { clearAccessToken, getAccessToken, setAccessToken } from '../../shared/api/apiClient.js'
import {
  changeCurrentUserPassword,
  getCurrentUser,
  loginUser,
  loginUserWithGoogle,
  registerUser,
  updateCurrentUser,
} from './api/authApi.js'
import { AuthContext } from './AuthContext.js'

function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(() => !getAccessToken())

  useEffect(() => {
    if (!getAccessToken()) {
      return undefined
    }

    let active = true
    getCurrentUser()
      .then((currentUser) => {
        if (active) setUser(currentUser)
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

  async function register(payload) {
    await registerUser(payload)
    return login({ email: payload.email, password: payload.password })
  }

  function logout() {
    clearAccessToken()
    setUser(null)
    setReady(true)
  }

  async function updateProfile(payload) {
    const updated = await updateCurrentUser(payload)
    setUser(updated)
    return updated
  }

  async function changePassword(payload) {
    await changeCurrentUserPassword(payload)
  }

  return (
    <AuthContext.Provider value={{ user, ready, login, loginWithGoogle, register, logout, updateProfile, changePassword }}>
      {children}
    </AuthContext.Provider>
  )
}

export default AuthProvider