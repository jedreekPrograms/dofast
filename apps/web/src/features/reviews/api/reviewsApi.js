import { apiRequest } from '../../../shared/api/apiClient.js'

export function getReviewEligibility(jobId) {
  return apiRequest(`/reviews/jobs/${jobId}/eligibility`)
}

export function createReview({ jobId, rating, comment }) {
  return apiRequest('/reviews', {
    method: 'POST',
    body: JSON.stringify({ jobId, rating, comment }),
  })
}

export function getPublicProfile(userId) {
  return apiRequest(`/users/${userId}/profile`, { auth: false })
}

export function getReceivedReviews(userId, { page = 0, size = 10 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest(`/reviews/users/${userId}?${params.toString()}`, { auth: false })
}
