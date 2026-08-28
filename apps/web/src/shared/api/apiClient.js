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
  const { response, payload } = await performRequest(path, options, 'json')
  void response
  return payload
}

export async function apiDownload(path, options = {}) {
  const { payload } = await performRequest(path, options, 'blob')
  return payload
}

async function performRequest(path, options, responseMode) {
  const { auth = true, headers = {}, ...fetchOptions } = options
  const requestHeaders = { ...headers }
  const token = auth ? getAccessToken() : null

  if (shouldSetJsonContentType(fetchOptions.body) && !requestHeaders['Content-Type']) {
    requestHeaders['Content-Type'] = 'application/json'
  }
  if (token) {
    requestHeaders.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...fetchOptions,
    headers: requestHeaders,
  })

  if (!response.ok) {
    const payload = await readErrorPayload(response)
    const message = payload && typeof payload === 'object' && payload.message
      ? payload.message
      : `API request failed with status ${response.status}`
    throw new ApiError(message, response.status, payload)
  }

  if (response.status === 204) {
    return { response, payload: null }
  }

  if (responseMode === 'blob') {
    return { response, payload: await response.blob() }
  }

  const text = await response.text()
  return { response, payload: parsePayload(text) }
}

async function readErrorPayload(response) {
  const text = await response.text()
  return parsePayload(text)
}

function parsePayload(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function shouldSetJsonContentType(body) {
  if (!body) return false
  if (typeof FormData !== 'undefined' && body instanceof FormData) return false
  if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) return false
  if (typeof Blob !== 'undefined' && body instanceof Blob) return false
  if (typeof ArrayBuffer !== 'undefined' && body instanceof ArrayBuffer) return false
  return true
}
