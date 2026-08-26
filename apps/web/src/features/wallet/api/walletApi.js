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
