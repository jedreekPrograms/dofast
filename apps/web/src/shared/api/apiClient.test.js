import assert from 'node:assert/strict'
import test from 'node:test'

import {
  apiRequest,
  clearAccessToken,
  getAccessToken,
  logoutAuthSession,
  refreshAccessToken,
  setAccessToken,
} from './apiClient.js'

function response(status, payload = null) {
  const body = payload === null ? '' : JSON.stringify(payload)
  return {
    status,
    ok: status >= 200 && status < 300,
    async text() {
      return body
    },
    async blob() {
      return new Blob([body])
    },
  }
}

function installDocument(cookie = '') {
  globalThis.document = { cookie }
}

function cleanupGlobals() {
  delete globalThis.document
  delete globalThis.window
  delete globalThis.fetch
  clearAccessToken()
}

test.afterEach(cleanupGlobals)

test('keeps access token only in module memory and never touches sessionStorage', () => {
  globalThis.window = {
    sessionStorage: {
      getItem() {
        throw new Error('sessionStorage must not be read')
      },
      setItem() {
        throw new Error('sessionStorage must not be written')
      },
      removeItem() {
        throw new Error('sessionStorage must not be written')
      },
    },
  }

  setAccessToken('short-lived-access-token')
  assert.equal(getAccessToken(), 'short-lived-access-token')
  clearAccessToken()
  assert.equal(getAccessToken(), null)
})

test('coalesces concurrent 401 responses into one refresh and retries with the fresh bearer', async () => {
  installDocument('dofast_csrf=csrf-token')
  setAccessToken('expired-access')

  let refreshCalls = 0
  let protectedCalls = 0
  globalThis.fetch = async (url, options = {}) => {
    if (url.endsWith('/users/session/refresh')) {
      refreshCalls += 1
      await new Promise((resolve) => setTimeout(resolve, 10))
      assert.equal(options.credentials, 'include')
      assert.equal(options.headers['X-CSRF-Token'], 'csrf-token')
      return response(200, {
        accessToken: 'fresh-access',
        tokenType: 'Bearer',
        expiresInMs: 600000,
        user: { id: 1, email: 'user@example.com' },
      })
    }

    protectedCalls += 1
    if (options.headers.Authorization === 'Bearer expired-access') {
      return response(401, { status: 401, error: 'Unauthorized' })
    }
    assert.equal(options.headers.Authorization, 'Bearer fresh-access')
    return response(200, { ok: true })
  }

  const [first, second] = await Promise.all([
    apiRequest('/protected/one'),
    apiRequest('/protected/two'),
  ])

  assert.deepEqual(first, { ok: true })
  assert.deepEqual(second, { ok: true })
  assert.equal(refreshCalls, 1)
  assert.equal(protectedCalls, 4)
  assert.equal(getAccessToken(), 'fresh-access')
})

test('failed refresh clears the in-memory bearer', async () => {
  installDocument('dofast_csrf=csrf-token')
  setAccessToken('expired-access')
  globalThis.fetch = async () => response(403, { message: 'Nieprawidłowy token CSRF' })

  await assert.rejects(refreshAccessToken(), (error) => error.status === 403)
  assert.equal(getAccessToken(), null)
})

test('logout sends the CSRF header and clears the bearer even on network failure', async () => {
  installDocument('dofast_csrf=csrf-token')
  setAccessToken('current-access')

  let seenOptions
  globalThis.fetch = async (_url, options) => {
    seenOptions = options
    throw new Error('network unavailable')
  }

  await assert.rejects(logoutAuthSession(), /network unavailable/)
  assert.equal(seenOptions.method, 'POST')
  assert.equal(seenOptions.credentials, 'include')
  assert.equal(seenOptions.headers['X-CSRF-Token'], 'csrf-token')
  assert.equal(getAccessToken(), null)
})
