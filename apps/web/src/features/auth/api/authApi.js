import { apiRequest } from '../../../shared/api/apiClient.js'

export function registerUser(payload) {
  return apiRequest('/users', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(payload),
  })
}

export function loginUser(payload) {
  return apiRequest('/users/login', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(payload),
  })
}

export function loginUserWithGoogle(credential) {
  return apiRequest('/users/login/google', {
    method: 'POST',
    auth: false,
    body: JSON.stringify({ credential }),
  })
}

export function createAppleLoginChallenge() {
  return apiRequest('/users/login/apple/challenge', {
    method: 'POST',
    auth: false,
  })
}

export function loginUserWithApple(payload) {
  return apiRequest('/users/login/apple', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(payload),
  })
}

export function getCurrentUser() {
  return apiRequest('/users/me')
}

export function updateCurrentUser(payload) {
  return apiRequest('/users/me/profile', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function changeCurrentUserPassword(payload) {
  return apiRequest('/users/me/password', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}