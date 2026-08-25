import { apiRequest } from '../../../shared/api/apiClient.js'

export function getJobs(filters, options = {}) {
  const params = new URLSearchParams()

  if (filters.query?.trim()) params.set('query', filters.query.trim())
  if (filters.minPrice !== '') params.set('minPrice', filters.minPrice)
  if (filters.maxPrice !== '') params.set('maxPrice', filters.maxPrice)
  params.set('page', String(filters.page ?? 0))
  params.set('size', String(filters.size ?? 12))

  return apiRequest(`/jobs?${params.toString()}`, { ...options, auth: false })
}

export function getJob(id, options = {}) {
  return apiRequest(`/jobs/${id}`, options)
}

export function getMyJobs(options = {}) {
  return apiRequest('/jobs/my', options)
}

export function createRouteQuote(payload) {
  return apiRequest('/routing/quotes', { method: 'POST', body: JSON.stringify(payload) })
}

export function getJobRoute(id, options = {}) {
  return apiRequest(`/jobs/${id}/route`, options)
}

export function getLiveTracking(id, options = {}) {
  return apiRequest(`/jobs/${id}/tracking`, options)
}

export function updateLiveTracking(id, payload) {
  return apiRequest(`/jobs/${id}/tracking/location`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function confirmJobPickup(id) {
  return apiRequest(`/jobs/${id}/tracking/pickup`, { method: 'POST' })
}

export function createJob(payload) {
  return apiRequest('/jobs', { method: 'POST', body: JSON.stringify(payload) })
}

export function acceptJob(id) {
  return apiRequest(`/jobs/${id}/accept`, { method: 'POST' })
}

export function requestJobCompletion(id) {
  return apiRequest(`/jobs/${id}/completion`, { method: 'POST' })
}

export function confirmJobCompletion(id) {
  return apiRequest(`/jobs/${id}/confirm`, { method: 'POST' })
}

export function cancelJob(id) {
  return apiRequest(`/jobs/${id}/cancel`, { method: 'POST' })
}
