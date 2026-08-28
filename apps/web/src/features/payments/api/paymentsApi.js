import { apiRequest } from '../../../shared/api/apiClient.js'

export function getPlatformFeePolicy(options = {}) {
  return apiRequest('/payments/platform-fee-policy', options)
}

export function getPlatformFeeQuote(amount, jobId = null, options = {}) {
  const params = new URLSearchParams({ amount: String(amount) })
  if (jobId) params.set('jobId', String(jobId))
  return apiRequest(`/payments/platform-fee-quote?${params.toString()}`, options)
}
