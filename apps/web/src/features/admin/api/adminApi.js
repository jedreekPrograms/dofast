import { apiRequest } from '../../../shared/api/apiClient.js'

export function getAdminOverview() {
  return apiRequest('/admin/overview')
}

export function getAdminUsers() {
  return apiRequest('/admin/users')
}

export function updateAdminUserStatus(userId, status) {
  return apiRequest(`/admin/users/${userId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}
