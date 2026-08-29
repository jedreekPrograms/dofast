import { apiRequest } from '../../../shared/api/apiClient.js'

export function getWallet() {
  return apiRequest('/wallet')
}

export function getWalletTransactions() {
  return apiRequest('/wallet/transactions')
}

export function createWalletTopUpIntent(amount, requestId) {
  return apiRequest('/payments/create-intent', {
    method: 'POST',
    body: JSON.stringify({ amount, requestId }),
  })
}

export function getPayoutEligibility() {
  return apiRequest('/wallet/payouts/eligibility')
}

export function getPayoutOnboardingStatus() {
  return apiRequest('/wallet/payouts/onboarding/status')
}

export function refreshPayoutOnboardingStatus() {
  return apiRequest('/wallet/payouts/onboarding/refresh', { method: 'POST' })
}

export function createPayoutOnboardingLink() {
  return apiRequest('/wallet/payouts/onboarding/link', { method: 'POST' })
}

export function getPayouts() {
  return apiRequest('/wallet/payouts')
}

export function requestPayout(amount, requestId) {
  return apiRequest('/wallet/payouts', {
    method: 'POST',
    body: JSON.stringify({ amount, requestId }),
  })
}

export function cancelPayout(payoutId) {
  return apiRequest(`/wallet/payouts/${payoutId}/cancel`, {
    method: 'POST',
  })
}
