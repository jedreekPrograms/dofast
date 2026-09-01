const API_BASE_URL = import.meta.env?.VITE_API_BASE_URL || '/api'
const CSRF_COOKIE = 'dofast_csrf'
const CSRF_HEADER = 'X-CSRF-Token'

let accessToken = null
let accessTokenExpiresAt = null
let refreshPromise = null
const accessTokenListeners = new Set()

export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token, expiresInMs = null) {
  const normalizedToken = typeof token === 'string' && token.trim() ? token : null
  const ttl = Number(expiresInMs)
  const expiresAt = normalizedToken && Number.isFinite(ttl) && ttl > 0
    ? Date.now() + ttl
    : null

  if (normalizedToken === accessToken && expiresAt === accessTokenExpiresAt) return
  accessToken = normalizedToken
  accessTokenExpiresAt = expiresAt
  for (const listener of accessTokenListeners) {
    listener(accessToken, accessTokenExpiresAt)
  }
}

export function clearAccessToken() {
  setAccessToken(null)
}

export function subscribeAccessToken(listener) {
  accessTokenListeners.add(listener)
  listener(accessToken, accessTokenExpiresAt)
  return () => accessTokenListeners.delete(listener)
}

export function hasRefreshSessionHint() {
  return Boolean(readCookie(CSRF_COOKIE))
}

export async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

export async function logoutAuthSession() {
  const csrfToken = readCookie(CSRF_COOKIE)
  const headers = csrfToken ? { [CSRF_HEADER]: csrfToken } : {}

  try {
    await fetch(`${API_BASE_URL}/users/session/logout`, {
      method: 'POST',
      credentials: 'include',
      headers,
    })
  } finally {
    clearAccessToken()
  }
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
  const {
    auth = true,
    retryAuth = true,
    headers = {},
    ...fetchOptions
  } = options
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
    credentials: fetchOptions.credentials || 'include',
    headers: requestHeaders,
  })

  if (response.status === 401 && auth && retryAuth) {
    await refreshAccessToken()
    return performRequest(path, { ...options, retryAuth: false }, responseMode)
  }

  if (!response.ok) {
    throw await toApiError(response)
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

async function performRefresh() {
  const csrfToken = readCookie(CSRF_COOKIE)
  if (!csrfToken) {
    clearAccessToken()
    throw new ApiError('Brak aktywnej sesji odświeżania', 401, null)
  }

  const response = await fetch(`${API_BASE_URL}/users/session/refresh`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      [CSRF_HEADER]: csrfToken,
    },
  })

  if (!response.ok) {
    clearAccessToken()
    throw await toApiError(response)
  }

  const payload = parsePayload(await response.text())
  if (!payload || typeof payload.accessToken !== 'string' || !payload.accessToken.trim()) {
    clearAccessToken()
    throw new ApiError('Serwer nie zwrócił poprawnego tokenu dostępu', 500, payload)
  }

  setAccessToken(payload.accessToken, payload.expiresInMs)
  return payload
}

async function toApiError(response) {
  const payload = await readErrorPayload(response)
  const message = payload && typeof payload === 'object' && payload.message
    ? payload.message
    : `API request failed with status ${response.status}`
  return new ApiError(message, response.status, payload)
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

function readCookie(name) {
  if (typeof document === 'undefined' || !document.cookie) return null
  const prefix = `${name}=`
  for (const cookie of document.cookie.split(';')) {
    const value = cookie.trim()
    if (value.startsWith(prefix)) {
      return decodeURIComponent(value.substring(prefix.length))
    }
  }
  return null
}

function shouldSetJsonContentType(body) {
  if (!body) return false
  if (typeof FormData !== 'undefined' && body instanceof FormData) return false
  if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) return false
  if (typeof Blob !== 'undefined' && body instanceof Blob) return false
  if (typeof ArrayBuffer !== 'undefined' && body instanceof ArrayBuffer) return false
  return true
}
