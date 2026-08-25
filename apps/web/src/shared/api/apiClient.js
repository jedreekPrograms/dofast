const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const TOKEN_KEY = 'dofast.accessToken'

export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

export function getAccessToken() {
  return window.sessionStorage.getItem(TOKEN_KEY)
}

export function setAccessToken(token) {
  window.sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearAccessToken() {
  window.sessionStorage.removeItem(TOKEN_KEY)
}

export async function apiRequest(path, options = {}) {
  const { auth = true, headers = {}, ...fetchOptions } = options
  const requestHeaders = { ...headers }
  const token = auth ? getAccessToken() : null

  if (fetchOptions.body && !requestHeaders['Content-Type']) {
    requestHeaders['Content-Type'] = 'application/json'
  }
  if (token) {
    requestHeaders.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...fetchOptions,
    headers: requestHeaders,
  })

  let payload = null
  if (response.status !== 204) {
    const text = await response.text()
    if (text) {
      try {
        payload = JSON.parse(text)
      } catch {
        payload = text
      }
    }
  }

  if (!response.ok) {
    const message = payload && typeof payload === 'object' && payload.message
      ? payload.message
      : `API request failed with status ${response.status}`
    throw new ApiError(message, response.status, payload)
  }

  return payload
}
