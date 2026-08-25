import { apiRequest } from '../../../shared/api/apiClient.js'

export function getMyDisputes() {
  return apiRequest('/disputes/my')
}

export function getDispute(disputeId) {
  return apiRequest(`/disputes/${disputeId}`)
}

export function openDispute(payload) {
  return apiRequest('/disputes', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function cancelDispute(disputeId) {
  return apiRequest(`/disputes/${disputeId}/cancel`, {
    method: 'POST',
  })
}
