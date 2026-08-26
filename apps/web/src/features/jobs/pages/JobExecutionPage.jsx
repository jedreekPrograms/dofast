import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getJob } from '../api/jobsApi.js'
import JobLocationPage from './JobLocationPage.jsx'
import JobRoutePage from './JobRoutePage.jsx'

function JobExecutionPage() {
  const { jobId } = useParams()
  const [mode, setMode] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getJob(jobId, { signal: controller.signal })
      .then((job) => setMode(job.fulfillmentMode))
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message || 'Nie udało się pobrać typu zlecenia.')
      })
    return () => controller.abort()
  }, [jobId])

  if (error) return <main><div className="form-message form-message--error">{error}</div></main>
  if (!mode) return <main><div className="page-state">Pobieranie danych realizacji…</div></main>
  return mode === 'ON_SITE' ? <JobLocationPage /> : <JobRoutePage />
}

export default JobExecutionPage
