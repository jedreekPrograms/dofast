import { useCallback, useEffect, useMemo, useState } from 'react'
import UserTrustCard from '../../reviews/components/UserTrustCard.jsx'
import {
  acceptJobProposal,
  getJobProposals,
  submitJobProposal,
  withdrawJobProposal,
} from '../api/jobsApi.js'
import './JobProposalPanel.css'

const STATUS_LABELS = {
  SUBMITTED: 'Aktywna',
  ACCEPTED: 'Wybrana',
  REJECTED: 'Niewybrana',
  WITHDRAWN: 'Wycofana',
}

const priceFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
})

const dateFormatter = new Intl.DateTimeFormat('pl-PL', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function JobProposalPanel({ job, currentUserId, onJobUpdated, onSuccess }) {
  const isCreator = job.createdById === currentUserId
  const isWorker = job.takenById === currentUserId
  const [proposals, setProposals] = useState([])
  const [loading, setLoading] = useState(true)
  const [busyAction, setBusyAction] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [amount, setAmount] = useState(String(job.price ?? ''))

  const ownProposal = useMemo(() => {
    if (isCreator) return null
    return proposals.find((proposal) => proposal.proposerId === currentUserId) || null
  }, [currentUserId, isCreator, proposals])

  const loadProposals = useCallback(async ({ quiet = false } = {}) => {
    if (!currentUserId) {
      setProposals([])
      setLoading(false)
      return
    }
    if (!quiet) setLoading(true)
    setError('')
    try {
      setProposals(await getJobProposals(job.id))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać propozycji.')
    } finally {
      if (!quiet) setLoading(false)
    }
  }, [currentUserId, job.id])

  useEffect(() => {
    loadProposals()
  }, [loadProposals])

  useEffect(() => {
    setAmount(String(job.price ?? ''))
  }, [job.price])

  async function handleSubmit(event) {
    event.preventDefault()
    setBusyAction('submit')
    setError('')

    const payload = {
      amount: null,
      message: message.trim() || null,
    }

    if (job.priceNegotiationEnabled) {
      const numericAmount = Number(amount)
      if (!Number.isFinite(numericAmount) || numericAmount < 0.01) {
        setError('Podaj prawidłową kwotę propozycji.')
        setBusyAction('')
        return
      }
      payload.amount = numericAmount
    }

    try {
      const created = await submitJobProposal(job.id, payload)
      setProposals([created])
      setMessage('')
      onSuccess?.('Propozycja została wysłana. Zlecający zobaczy ją razem z Twoim profilem zaufania.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wysłać propozycji.')
    } finally {
      setBusyAction('')
    }
  }

  async function handleWithdraw(proposalId) {
    setBusyAction(`withdraw-${proposalId}`)
    setError('')
    try {
      await withdrawJobProposal(job.id, proposalId)
      await loadProposals({ quiet: true })
      onSuccess?.('Propozycja została wycofana.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wycofać propozycji.')
    } finally {
      setBusyAction('')
    }
  }

  async function handleAccept(proposalId) {
    setBusyAction(`accept-${proposalId}`)
    setError('')
    try {
      const accepted = await acceptJobProposal(job.id, proposalId)
      onJobUpdated?.(accepted.job)
      onSuccess?.('Wykonawca został wybrany, a escrow dopasowane do zaakceptowanej ceny.')
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zaakceptować propozycji.')
    } finally {
      setBusyAction('')
    }
  }

  if (!currentUserId) return null

  return (
    <section className="job-details-panel job-proposal-panel">
      <div className="job-proposal-panel__heading">
        <div>
          <span className="eyebrow">Wybór wykonawcy</span>
          <h2>{isCreator ? 'Propozycje wykonawców' : 'Twoja propozycja'}</h2>
        </div>
        <div className="job-proposal-panel__badges" aria-label="Zasady zlecenia">
          <span>Wybór przez zlecającego</span>
          <span>{job.priceNegotiationEnabled ? 'Cena do negocjacji' : 'Cena stała'}</span>
        </div>
      </div>

      <p className="job-proposal-panel__privacy">
        {isCreator
          ? 'Propozycje są prywatne. Tylko Ty widzisz wszystkich kandydatów, ich kwoty i wiadomości.'
          : 'Inni wykonawcy nie widzą Twojej kwoty ani wiadomości. Ty również nie widzisz ich propozycji.'}
      </p>

      {error && <div className="form-message form-message--error" role="alert">{error}</div>}
      {loading && <div className="page-state">Pobieranie propozycji…</div>}

      {!loading && isCreator && (
        <CreatorProposalList
          job={job}
          proposals={proposals}
          busyAction={busyAction}
          onAccept={handleAccept}
          onRefresh={() => loadProposals()}
        />
      )}

      {!loading && !isCreator && job.status === 'OPEN' && !ownProposal && (
        <form className="job-proposal-form" onSubmit={handleSubmit}>
          {job.priceNegotiationEnabled ? (
            <label className="field">
              <span>Twoja cena</span>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                disabled={Boolean(busyAction)}
                required
              />
              <small>Opublikowany budżet to {priceFormatter.format(Number(job.price))}. Zlecający dopłaci różnicę do escrow dopiero, jeśli wybierze wyższą propozycję.</small>
            </label>
          ) : (
            <div className="job-proposal-form__fixed-price">
              <span>Stałe wynagrodzenie</span>
              <strong>{priceFormatter.format(Number(job.price))}</strong>
              <small>W tym zleceniu wybierany jest wykonawca, ale cena nie podlega negocjacji.</small>
            </div>
          )}

          <label className="field">
            <span>Wiadomość do zlecającego <small>(opcjonalnie)</small></span>
            <textarea
              rows={4}
              maxLength={1000}
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              placeholder="Np. mogę zrobić to dziś po 17:00, mam rower z dużym koszem."
              disabled={Boolean(busyAction)}
            />
          </label>

          <button className="button button--primary" type="submit" disabled={Boolean(busyAction)}>
            {busyAction === 'submit' ? 'Wysyłanie…' : 'Wyślij propozycję'}
          </button>
        </form>
      )}

      {!loading && !isCreator && ownProposal && (
        <OwnProposalCard
          proposal={ownProposal}
          job={job}
          isWorker={isWorker}
          busyAction={busyAction}
          onWithdraw={handleWithdraw}
        />
      )}

      {!loading && !isCreator && !ownProposal && job.status !== 'OPEN' && (
        <div className="page-state">Zlecenie nie przyjmuje już nowych propozycji.</div>
      )}
    </section>
  )
}

function CreatorProposalList({ job, proposals, busyAction, onAccept, onRefresh }) {
  const activeCount = proposals.filter((proposal) => proposal.status === 'SUBMITTED').length

  return (
    <div className="job-proposal-list">
      <div className="job-proposal-list__toolbar">
        <span>{activeCount} {activeCount === 1 ? 'aktywna propozycja' : 'aktywnych propozycji'}</span>
        {job.status === 'OPEN' && (
          <button className="button button--secondary" type="button" disabled={Boolean(busyAction)} onClick={onRefresh}>
            Odśwież
          </button>
        )}
      </div>

      {proposals.length === 0 && (
        <div className="page-state">Nikt jeszcze nie wysłał propozycji. Zlecenie pozostaje otwarte.</div>
      )}

      {proposals.map((proposal) => (
        <article className={`job-proposal-card job-proposal-card--${proposal.status.toLowerCase()}`} key={proposal.id}>
          <UserTrustCard userId={proposal.proposerId} roleLabel="Kandydat" compact />
          <div className="job-proposal-card__offer">
            <div className="job-proposal-card__amount-row">
              <div>
                <span>Proponowane wynagrodzenie</span>
                <strong>{priceFormatter.format(Number(proposal.amount))}</strong>
              </div>
              <span className={`job-proposal-status job-proposal-status--${proposal.status.toLowerCase()}`}>
                {STATUS_LABELS[proposal.status] || proposal.status}
              </span>
            </div>
            {proposal.message && <p>{proposal.message}</p>}
            <small>Wysłano {dateFormatter.format(new Date(proposal.createdAt))}</small>
            {proposal.status === 'SUBMITTED' && job.status === 'OPEN' && (
              <button
                className="button button--primary"
                type="button"
                disabled={Boolean(busyAction)}
                onClick={() => onAccept(proposal.id)}
              >
                {busyAction === `accept-${proposal.id}` ? 'Wybieranie…' : `Wybierz za ${priceFormatter.format(Number(proposal.amount))}`}
              </button>
            )}
          </div>
        </article>
      ))}
    </div>
  )
}

function OwnProposalCard({ proposal, job, isWorker, busyAction, onWithdraw }) {
  return (
    <article className={`job-proposal-card job-proposal-card--own job-proposal-card--${proposal.status.toLowerCase()}`}>
      <div className="job-proposal-card__offer">
        <div className="job-proposal-card__amount-row">
          <div>
            <span>Twoja cena</span>
            <strong>{priceFormatter.format(Number(proposal.amount))}</strong>
          </div>
          <span className={`job-proposal-status job-proposal-status--${proposal.status.toLowerCase()}`}>
            {STATUS_LABELS[proposal.status] || proposal.status}
          </span>
        </div>
        {proposal.message && <p>{proposal.message}</p>}
        <small>Wysłano {dateFormatter.format(new Date(proposal.createdAt))}</small>

        {proposal.status === 'SUBMITTED' && job.status === 'OPEN' && (
          <button
            className="button button--secondary"
            type="button"
            disabled={Boolean(busyAction)}
            onClick={() => onWithdraw(proposal.id)}
          >
            {busyAction === `withdraw-${proposal.id}` ? 'Wycofywanie…' : 'Wycofaj propozycję'}
          </button>
        )}

        {proposal.status === 'WITHDRAWN' && (
          <div className="job-proposal-card__note">Wycofanej propozycji nie można wysłać ponownie dla tego samego zlecenia.</div>
        )}
        {proposal.status === 'ACCEPTED' && isWorker && (
          <div className="job-proposal-card__note job-proposal-card__note--success">Zostałeś wybrany do realizacji tego zlecenia.</div>
        )}
        {proposal.status === 'REJECTED' && (
          <div className="job-proposal-card__note">Zlecający wybrał inną propozycję.</div>
        )}
      </div>
    </article>
  )
}

export default JobProposalPanel
