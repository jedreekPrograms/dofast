import { apiRequest } from '../../../shared/api/apiClient.js'

export function getAdminOverview() {
  return apiRequest('/admin/overview')
}

export function getAdminUsers() {
  return apiRequest('/admin/users')
}

export function getAdminUserReactivationAudits(userId) {
  return apiRequest(`/admin/users/${userId}/reactivation-audits`)
}

export function getFinanceReconciliation() {
  return apiRequest('/admin/finance/reconciliation')
}

export function updateAdminUserStatus(userId, status, reason) {
  return apiRequest(`/admin/users/${userId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, reason }),
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

export function resolveAdminDispute(disputeId, resolution, note, approvedExpenseAmount = null) {
  return apiRequest(`/admin/disputes/${disputeId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ resolution, note, approvedExpenseAmount }),
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

export function getAdminJobReportEnforcement(reportId) {
  return apiRequest(`/admin/job-reports/${reportId}/enforcement`)
}

export function getAdminJobReportAccountEnforcement(reportId) {
  return apiRequest(`/admin/job-reports/${reportId}/account-enforcement`)
}

export function moderateAdminJobReport(reportId, status, note = '') {
  return apiRequest(`/admin/job-reports/${reportId}`, {
    method: 'PATCH',
    body: JSON.stringify({ status, note: note.trim() || null }),
  })
}

export function enforceAdminJobReport(reportId, reason = '') {
  return apiRequest(`/admin/job-reports/${reportId}/enforcement`, {
    method: 'POST',
    body: JSON.stringify({
      action: 'CANCEL_OPEN_JOB',
      reason: reason.trim() || null,
    }),
  })
}

export function enforceAdminJobReportAccount(reportId, reason = '', action = 'SUSPEND_JOB_OWNER') {
  return apiRequest(`/admin/job-reports/${reportId}/account-enforcement`, {
    method: 'POST',
    body: JSON.stringify({
      action,
      reason: reason.trim() || null,
    }),
  })
}

export function getAdminPayouts({ status = '', page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.set('status', status)
  return apiRequest(`/admin/payouts?${params.toString()}`)
}

export function getAdminPayoutEvents(payoutId) {
  return apiRequest(`/admin/payouts/${payoutId}/events`)
}

export function retryAdminPayout(payoutId) {
  return apiRequest(`/admin/payouts/${payoutId}/retry`, { method: 'POST' })
}

export function failAdminPayout(payoutId, reason) {
  return apiRequest(`/admin/payouts/${payoutId}/fail`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason.trim() }),
  })
}
