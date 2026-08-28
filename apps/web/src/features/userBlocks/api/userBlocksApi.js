import { apiRequest } from '../../../shared/api/apiClient.js'

export function getMyUserBlocks() {
  return apiRequest('/user-blocks')
}

export function blockUser(userId) {
  return apiRequest(`/user-blocks/${userId}`, {
    method: 'PUT',
  })
}

export function unblockUser(userId) {
  return apiRequest(`/user-blocks/${userId}`, {
    method: 'DELETE',
  })
}
