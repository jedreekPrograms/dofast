import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import { getMyJobs } from '../../jobs/api/jobsApi.js'
import { cancelDispute, getDispute, getMyDisputes, openDispute } from '../api/disputesApi.js'
import './DisputesPage.css'

const REASONS = {
  NOT_COMPLETED: 'Zlecenie niewykonane',
  QUALITY_ISSUE: 'Problem z jakością wykonania',
  NO_SHOW: 'Brak kontaktu / niestawienie się',
  PAYMENT_ISSUE: 'Problem z płatnością',
  SAFETY_CONCERN: 'Problem bezpieczeństwa',
  OTHER: 'Inny powód',
}

const STATUSES = {
  OPEN: 'Otwarty',
  UNDER_REVIEW: 'W analizie',
  RESOLVED: 'Rozstrzygnięty',
  CANCELLED: 'Anulowany',
}

const RESOLUTIONS = {
  RELEASE_TO_WORKER: 'Środki wypłacone wykonawcy',
  REFUND_TO_REQUESTER: 'Zwrot środków zlecającemu',
  RESUME_JOB: 'Zlecenie wznowione',
}

function DisputesPage() {
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  const [disputes, setDisputes] = useState([])
  const [jobs, setJobs] = useState([])
  const [selected, setSelected] = useState(null)
  const [selectedJobId, setSelectedJobId] = useState(searchParams.get('jobId') || '')
  const [reason, setReason] = useState('OTHER')
  const [description, setDescription] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([getMyDisputes(), getMyJobs()])
      .then(([disputeData, jobData]) => {
        if (!active) return
        setDisputes(disputeData)
        setJobs(jobData)
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać sporów.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  const eligibleJobs = useMemo(() => jobs.filter((job) => (
    job.status === 'IN_PROGRESS' || job.status === 'COMPLETION_REQUESTED'
  )), [jobs])

  async function handleOpen(event) {
    event.preventDefault()
    if (!selectedJobId) {
      setError('Wybierz zlecenie, którego dotyczy spór.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const detail = await openDispute({
        jobId: Number(selectedJobId),
        reason,
        description,
      })
      setDisputes((current) => [detail.dispute, ...current])
      setJobs((current) => current.map((job) => job.id === Number(selectedJobId)
        ? { ...job, status: 'DISPUTED' }
        : job))
      setSelected(detail)
      setDescription('')
      setSelectedJobId('')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się otworzyć sporu.')
    } finally {
      setBusy(false)
    }
  }

  async function showDetails(disputeId) {
    setBusy(true)
    setError('')
    try {
      setSelected(await getDispute(disputeId))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać szczegółów sporu.')
    } finally {
      setBusy(false)
    }
  }

  async function handleCancel() {
    if (!selected) return
    setBusy(true)
    setError('')
    try {
      const detail = await cancelDispute(selected.dispute.id)
      setSelected(detail)
      setDisputes((current) => current.map((item) => item.id === detail.dispute.id ? detail.dispute : item))
      setJobs((current) => current.map((job) => job.id === detail.dispute.jobId
        ? { ...job, status: detail.dispute.previousJobStatus }
        : job))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się anulować sporu.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="disputes-page">
      <header className="page-heading">
        <span className="eyebrow">Bezpieczeństwo transakcji</span>
        <h1>Spory</h1>
        <p>Jeśli aktywne zlecenie wymaga interwencji, środki pozostają w escrow do czasu rozstrzygnięcia.</p>
      </header>

      {error && <div className="form-message form-message--error">{error}</div>}

      <div className="disputes-layout">
        <section className="panel dispute-create">
          <div>
            <span className="eyebrow">Nowa sprawa</span>
            <h2>Otwórz spór</h2>
          </div>
          {eligibleJobs.length === 0 ? (
            <p className="disputes-muted">Nie masz teraz zlecenia, dla którego można otworzyć nowy spór.</p>
          ) : (
            <form onSubmit={handleOpen}>
              <label>
                Zlecenie
                <select value={selectedJobId} onChange={(event) => setSelectedJobId(event.target.value)} required>
                  <option value="">Wybierz zlecenie</option>
                  {eligibleJobs.map((job) => (
                    <option key={job.id} value={job.id}>#{job.id} — {job.title}</option>
                  ))}
                </select>
              </label>
              <label>
                Powód
                <select value={reason} onChange={(event) => setReason(event.target.value)}>
                  {Object.entries(REASONS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label>
                Opisz problem
                <textarea value={description} onChange={(event) => setDescription(event.target.value)} maxLength={4000} minLength={3} required rows={6} placeholder="Co się wydarzyło? Podaj konkretne informacje, które pomogą administratorowi ocenić sprawę." />
              </label>
              <button className="button button--danger" type="submit" disabled={busy}>Otwórz spór</button>
            </form>
          )}
        </section>

        <section className="panel dispute-list-panel">
          <div>
            <span className="eyebrow">Historia</span>
            <h2>Twoje sprawy</h2>
          </div>
          {loading && <div className="page-state">Pobieranie sporów…</div>}
          {!loading && disputes.length === 0 && <p className="disputes-muted">Nie masz jeszcze żadnych sporów.</p>}
          <div className="dispute-list">
            {disputes.map((dispute) => (
              <button className="dispute-row" type="button" key={dispute.id} onClick={() => showDetails(dispute.id)}>
                <div>
                  <strong>#{dispute.id} · {dispute.jobTitle}</strong>
                  <span>{REASONS[dispute.reason] || dispute.reason}</span>
                </div>
                <span className={`status-pill status-pill--${dispute.status.toLowerCase()}`}>{STATUSES[dispute.status] || dispute.status}</span>
              </button>
            ))}
          </div>
        </section>
      </div>

      {selected && (
        <section className="panel dispute-detail">
          <div className="dispute-detail__header">
            <div>
              <span className="eyebrow">Sprawa #{selected.dispute.id}</span>
              <h2>{selected.dispute.jobTitle}</h2>
            </div>
            <span className={`status-pill status-pill--${selected.dispute.status.toLowerCase()}`}>{STATUSES[selected.dispute.status]}</span>
          </div>

          <div className="dispute-detail__grid">
            <div><span>Powód</span><strong>{REASONS[selected.dispute.reason] || selected.dispute.reason}</strong></div>
            <div><span>Otworzył</span><strong>{selected.dispute.openedById === user.id ? 'Ty' : 'Druga strona'}</strong></div>
            <div><span>Admin</span><strong>{selected.dispute.assignedAdminId ? `#${selected.dispute.assignedAdminId}` : 'Jeszcze nieprzypisany'}</strong></div>
            <div><span>Rozstrzygnięcie</span><strong>{selected.dispute.resolution ? RESOLUTIONS[selected.dispute.resolution] : 'Oczekuje'}</strong></div>
          </div>

          <p className="dispute-detail__description">{selected.dispute.description}</p>
          {selected.dispute.adminNote && <div className="dispute-admin-note"><strong>Uzasadnienie administratora</strong><p>{selected.dispute.adminNote}</p></div>}

          <div className="dispute-timeline">
            <h3>Historia sprawy</h3>
            {selected.events.map((event) => (
              <div className="dispute-event" key={event.id}>
                <div><strong>{event.eventType}</strong><span>{event.actorNickname}</span></div>
                {event.note && <p>{event.note}</p>}
                <time>{new Date(event.createdAt).toLocaleString('pl-PL')}</time>
              </div>
            ))}
          </div>

          {selected.dispute.status === 'OPEN' && selected.dispute.openedById === user.id && (
            <button className="button button--secondary" type="button" disabled={busy} onClick={handleCancel}>Anuluj spór</button>
          )}
        </section>
      )}
    </main>
  )
}

export default DisputesPage
