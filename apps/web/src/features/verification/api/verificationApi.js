import { apiRequest } from '../../../shared/api/apiClient.js'

export function getMyVerification() {
  return apiRequest('/verification/me')
}

export function requestIdentityVerification() {
  return apiRequest('/verification/request', {
    method: 'POST',
  })
}
