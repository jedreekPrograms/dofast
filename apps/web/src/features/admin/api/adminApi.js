import { apiRequest } from '../../../shared/api/apiClient.js'

export function getAdminOverview() {
  return apiRequest('/admin/overview')
}

export function getAdminUsers() {
  return apiRequest('/admin/users')
}

export function getFinanceReconciliation() {
  return apiRequest('/admin/finance/reconciliation')
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

export function getAdminDisputeMessages(disputeId, { beforeId = null, limit = 100 } = {}) {
  const params = new URLSearchParams({ limit: String(limit) })
  if (beforeId) params.set('beforeId', String(beforeId))
  return apiRequest(`/admin/disputes/${disputeId}/messages?${params.toString()}`)
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

export function getAdminVerifications({ status = '', page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.set('status', status)
  return apiRequest(`/admin/verifications?${params.toString()}`)
}

export function getAdminVerificationEvents(verificationId) {
  return apiRequest(`/admin/verifications/${verificationId}/events`)
}

export function decideAdminVerification(verificationId, decision, reason = '') {
  return apiRequest(`/admin/verifications/${verificationId}`, {
    method: 'PATCH',
    body: JSON.stringify({ decision, reason: reason || null }),
  })
}

export function getAdminJobReports({ status = '', page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.set('status', status)
  return apiRequest(`/admin/job-reports?${params.toString()}`)
}

export function moderateAdminJobReport(reportId, status, note = '') {
  return apiRequest(`/admin/job-reports/${reportId}`, {
    method: 'PATCH',
    body: JSON.stringify({ status, note: note.trim() || null }),
  })
}
