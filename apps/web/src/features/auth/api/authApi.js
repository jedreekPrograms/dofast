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
