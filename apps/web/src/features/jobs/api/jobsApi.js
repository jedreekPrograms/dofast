import { apiRequest } from '../../../shared/api/apiClient.js'

export function getJobs(filters, options = {}) {
  const params = new URLSearchParams()

  if (filters.query?.trim()) params.set('query', filters.query.trim())
  if (filters.category) params.set('category', filters.category)
  if (filters.minPrice !== '') params.set('minPrice', filters.minPrice)
  if (filters.maxPrice !== '') params.set('maxPrice', filters.maxPrice)
  params.set('page', String(filters.page ?? 0))
  params.set('size', String(filters.size ?? 12))

  return apiRequest(`/jobs?${params.toString()}`, { ...options, auth: false })
}

export function getJobCategories(options = {}) {
  return apiRequest('/job-categories', { ...options, auth: false })
}

export function getJob(id, options = {}) {
  return apiRequest(`/jobs/${id}`, options)
}

export function getJobLocation(id, options = {}) {
  return apiRequest(`/jobs/${id}/location`, options)
}

export function getMyJobs(options = {}) {
  return apiRequest('/jobs/my', options)
}

export function getSavedJobs(page = 0, size = 12, options = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest(`/saved-jobs?${params.toString()}`, options)
}

export function getSavedJobStatus(id, options = {}) {
  return apiRequest(`/saved-jobs/${id}/status`, options)
}

export function getSavedJobStatuses(jobIds, options = {}) {
  const params = new URLSearchParams()
  jobIds.forEach((jobId) => params.append('jobIds', String(jobId)))
  return apiRequest(`/saved-jobs/status?${params.toString()}`, options)
}

export function saveJob(id) {
  return apiRequest(`/saved-jobs/${id}`, { method: 'PUT' })
}

export function removeSavedJob(id) {
  return apiRequest(`/saved-jobs/${id}`, { method: 'DELETE' })
}

export function getSavedSearches(options = {}) {
  return apiRequest('/saved-searches', options)
}

export function createSavedSearch(payload) {
  return apiRequest('/saved-searches', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateSavedSearch(id, payload) {
  return apiRequest(`/saved-searches/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteSavedSearch(id) {
  return apiRequest(`/saved-searches/${id}`, { method: 'DELETE' })
}

export function createRouteQuote(payload) {
  return apiRequest('/routing/quotes', { method: 'POST', body: JSON.stringify(payload) })
}

export function getRouteModeEstimates(quoteId, options = {}) {
  return apiRequest(`/routing/quotes/${quoteId}/mode-estimates`, options)
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

export function confirmRouteCheckpoint(id) {
  return apiRequest(`/jobs/${id}/tracking/checkpoint`, { method: 'POST' })
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

export function getPendingJobCancellation(id, options = {}) {
  return apiRequest(`/jobs/${id}/cancellation`, options)
}

export function requestJobCancellation(id, reason) {
  return apiRequest(`/jobs/${id}/cancellation`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function approveJobCancellation(id) {
  return apiRequest(`/jobs/${id}/cancellation/approve`, { method: 'POST' })
}

export function declineJobCancellation(id) {
  return apiRequest(`/jobs/${id}/cancellation/decline`, { method: 'POST' })
}

export function withdrawJobCancellation(id) {
  return apiRequest(`/jobs/${id}/cancellation/withdraw`, { method: 'POST' })
}
