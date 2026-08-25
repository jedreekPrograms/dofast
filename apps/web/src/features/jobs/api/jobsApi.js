import { apiRequest } from '../../../shared/api/apiClient.js'

export function getJobs(filters, options = {}) {
  const params = new URLSearchParams()

  if (filters.query?.trim()) {
    params.set('query', filters.query.trim())
  }
  if (filters.minPrice !== '') {
    params.set('minPrice', filters.minPrice)
  }
  if (filters.maxPrice !== '') {
    params.set('maxPrice', filters.maxPrice)
  }

  params.set('page', String(filters.page ?? 0))
  params.set('size', String(filters.size ?? 12))

  return apiRequest(`/jobs?${params.toString()}`, options)
}
