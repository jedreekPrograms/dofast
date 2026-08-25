import { apiRequest } from '../../../shared/api/apiClient.js'

export function getNotifications({ unreadOnly = false, page = 0, size = 30 } = {}) {
  const params = new URLSearchParams({
    unreadOnly: String(unreadOnly),
    page: String(page),
    size: String(size),
  })
  return apiRequest(`/notifications?${params.toString()}`)
}

export function getUnreadNotificationCount() {
  return apiRequest('/notifications/unread-count')
}

export function markNotificationRead(id) {
  return apiRequest(`/notifications/${id}/read`, { method: 'POST' })
}

export function markAllNotificationsRead() {
  return apiRequest('/notifications/read-all', { method: 'POST' })
}
