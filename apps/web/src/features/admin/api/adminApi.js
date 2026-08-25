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

export function getAdminDisputes({ status = '', page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.set('status', status)
  return apiRequest(`/admin/disputes?${params.toString()}`)
}

export function getAdminDispute(disputeId) {
  return apiRequest(`/admin/disputes/${disputeId}`)
}

export function claimAdminDispute(disputeId) {
  return apiRequest(`/admin/disputes/${disputeId}/claim`, {
    method: 'POST',
  })
}

export function resolveAdminDispute(disputeId, resolution, note) {
  return apiRequest(`/admin/disputes/${disputeId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ resolution, note }),
  })
}
