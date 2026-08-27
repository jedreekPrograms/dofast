import { apiRequest } from '../../../shared/api/apiClient.js'

export function reportJob(jobId, payload) {
  return apiRequest(`/job-reports/jobs/${jobId}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getMyJobReports(options = {}) {
  return apiRequest('/job-reports/mine', options)
}
