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
