import { apiRequest } from '../../../shared/api/apiClient.js'

export function getWallet() {
  return apiRequest('/wallet')
}

export function getWalletTransactions() {
  return apiRequest('/wallet/transactions')
}
