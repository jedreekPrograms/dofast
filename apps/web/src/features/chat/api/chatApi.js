import { apiRequest } from '../../../shared/api/apiClient.js'

export function getConversations() {
  return apiRequest('/chat/conversations')
}

export function getChatHistory(jobId, { beforeId = null, limit = 50 } = {}) {
  const params = new URLSearchParams({ limit: String(limit) })
  if (beforeId) params.set('beforeId', String(beforeId))
  return apiRequest(`/chat/jobs/${jobId}/messages?${params.toString()}`)
}

export function sendChatMessage(jobId, content, clientMessageId) {
  return apiRequest(`/chat/jobs/${jobId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ content, clientMessageId }),
  })
}

export function markChatRead(jobId, lastMessageId) {
  return apiRequest(`/chat/jobs/${jobId}/read`, {
    method: 'POST',
    body: JSON.stringify({ lastMessageId }),
  })
}
