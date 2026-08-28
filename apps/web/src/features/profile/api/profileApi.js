import { apiRequest } from '../../../shared/api/apiClient.js'

export function getMyServiceCategories(options = {}) {
  return apiRequest('/users/me/service-categories', options)
}

export function updateMyServiceCategories(categoryIds) {
  return apiRequest('/users/me/service-categories', {
    method: 'PUT',
    body: JSON.stringify({ categoryIds }),
  })
}

export function getMyServiceArea(options = {}) {
  return apiRequest('/users/me/service-area', options)
}

export function updateMyServiceArea(payload) {
  return apiRequest('/users/me/service-area', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function clearMyServiceArea() {
  return apiRequest('/users/me/service-area', { method: 'DELETE' })
}
