import { useCallback, useEffect, useState } from 'react'
import JobCard from '../components/JobCard.jsx'
import { getSavedJobs, removeSavedJob } from '../api/jobsApi.js'
import './JobsPage.css'
import './SavedJobsPage.css'

const PAGE_SIZE = 12

function SavedJobsPage() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [removingId, setRemovingId] = useState(null)

  const loadSavedJobs = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      const data = await getSavedJobs(page, PAGE_SIZE, { signal })
      setResult(data)
      if (page > 0 && data.content.length === 0) {
        setPage((current) => Math.max(0, current - 1))
      }
    } catch (requestError) {
      if (requestError.name !== 'AbortError') {
        setError(requestError.message || 'Nie udało się pobrać zapisanych zleceń.')
      }
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [page])

  useEffect(() => {
    const controller = new AbortController()
    loadSavedJobs(controller.signal)
    return () => controller.abort()
  }, [loadSavedJobs])

  async function handleRemove(jobId) {
    setRemovingId(jobId)
    setError('')
    try {
      await removeSavedJob(jobId)
      await loadSavedJobs()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się usunąć zlecenia z zapisanych.')
    } finally {
      setRemovingId(null)
    }
  }

  const jobs = result?.content ?? []

  return (
    <main className="jobs-page saved-jobs-page">
      <header className="jobs-hero">
        <span className="jobs-hero__badge">Twoja lista</span>
        <h1>Zapisane zlecenia</h1>
        <p>Wróć do interesujących ofert później. Lista pokazuje wyłącznie zlecenia, które nadal są otwarte.</p>
      </header>

      <section className="jobs-results" aria-live="polite">
        <div className="jobs-results__heading">
          <div>
            <h2>Obserwowane</h2>
            {result && <p>{result.totalElements} zapisanych zleceń</p>}
          </div>
        </div>

        {loading && <div className="jobs-state">Pobieranie zapisanych zleceń…</div>}
        {!loading && error && <div className="jobs-state jobs-state--error">{error}</div>}
        {!loading && !error && jobs.length === 0 && (
          <div className="jobs-state">Nie masz jeszcze żadnych otwartych zapisanych zleceń.</div>
        )}

        {!loading && !error && jobs.length > 0 && (
          <div className="jobs-grid">
            {jobs.map((job) => (
              <div className="saved-job-card" key={job.id}>
                <JobCard job={job} />
                <button
                  className="button button--secondary saved-job-card__remove"
                  type="button"
                  disabled={removingId === job.id}
                  onClick={() => handleRemove(job.id)}
                >
                  {removingId === job.id ? 'Usuwanie…' : 'Usuń z zapisanych'}
                </button>
              </div>
            ))}
          </div>
        )}

        {!loading && !error && result && result.totalPages > 1 && (
          <nav className="jobs-pagination" aria-label="Paginacja zapisanych zleceń">
            <button
              type="button"
              className="button button--secondary"
              disabled={result.first}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
            >
              Poprzednia
            </button>
            <span>Strona {result.page + 1} z {result.totalPages}</span>
            <button
              type="button"
              className="button button--secondary"
              disabled={result.last}
              onClick={() => setPage((current) => current + 1)}
            >
              Następna
            </button>
          </nav>
        )}
      </section>
    </main>
  )
}

export default SavedJobsPage
