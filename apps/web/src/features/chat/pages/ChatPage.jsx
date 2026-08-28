import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import UserTrustCard from '../../reviews/components/UserTrustCard.jsx'
import { useRealtime } from '../../../shared/realtime/RealtimeContext.js'
import { blockUser, getMyUserBlocks, unblockUser } from '../../userBlocks/api/userBlocksApi.js'
import {
  getChatHistory,
  getConversations,
  markChatRead,
  sendChatMessage,
} from '../api/chatApi.js'
import './ChatPage.css'

const SENDABLE_STATUSES = new Set(['IN_PROGRESS', 'COMPLETION_REQUESTED', 'DISPUTED'])

const STATUS_LABELS = {
  OPEN: 'Otwarte',
  IN_PROGRESS: 'W realizacji',
  COMPLETION_REQUESTED: 'Do potwierdzenia',
  DISPUTED: 'Spór',
  DONE: 'Zakończone',
  CANCELLED: 'Anulowane',
}

function ChatPage() {
  const { user } = useAuth()
  const { status: realtimeStatus, subscribe } = useRealtime()
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedJobId = Number(searchParams.get('jobId')) || null

  const [conversations, setConversations] = useState([])
  const [selectedJobId, setSelectedJobId] = useState(requestedJobId)
  const [history, setHistory] = useState(null)
  const [draft, setDraft] = useState('')
  const [blockedUserIds, setBlockedUserIds] = useState(new Set())
  const [loading, setLoading] = useState(true)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [sending, setSending] = useState(false)
  const [blockUpdating, setBlockUpdating] = useState(false)
  const [error, setError] = useState('')

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.jobId === selectedJobId) || null,
    [conversations, selectedJobId],
  )

  const selectedBlockedByMe = selectedConversation
    ? blockedUserIds.has(selectedConversation.otherUserId)
    : false

  const loadConversations = useCallback(async () => {
    try {
      const data = await getConversations()
      setConversations(data)
      setSelectedJobId((current) => {
        if (current && data.some((conversation) => conversation.jobId === current)) return current
        if (requestedJobId && data.some((conversation) => conversation.jobId === requestedJobId)) return requestedJobId
        return data[0]?.jobId ?? null
      })
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać rozmów.')
    } finally {
      setLoading(false)
    }
  }, [requestedJobId])

  useEffect(() => {
    loadConversations()
    const interval = window.setInterval(loadConversations, 20000)
    return () => window.clearInterval(interval)
  }, [loadConversations])

  useEffect(() => {
    let active = true
    getMyUserBlocks()
      .then((blocks) => {
        if (active) setBlockedUserIds(new Set(blocks.map((block) => block.userId)))
      })
      .catch(() => {
        // Chat remains usable if the private block-list status cannot be hydrated.
      })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!selectedJobId) {
      setHistory(null)
      return undefined
    }

    let active = true
    setHistoryLoading(true)
    setError('')
    getChatHistory(selectedJobId)
      .then(async (response) => {
        if (!active) return
        setHistory(response)
        const latest = response.messages?.at(-1)
        if (latest) {
          await markChatRead(selectedJobId, latest.id)
          if (active) {
            setConversations((current) => current.map((conversation) => (
              conversation.jobId === selectedJobId
                ? { ...conversation, unreadCount: 0 }
                : conversation
            )))
          }
        }
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || 'Nie udało się pobrać historii czatu.')
      })
      .finally(() => {
        if (active) setHistoryLoading(false)
      })

    return () => { active = false }
  }, [selectedJobId])

  useEffect(() => {
    if (!selectedJobId) return undefined
    return subscribe(`/topic/chat/${selectedJobId}`, (message) => {
      setHistory((current) => {
        if (!current || current.messages.some((item) => item.id === message.id)) return current
        return { ...current, messages: [...current.messages, message] }
      })
      setConversations((current) => current.map((conversation) => (
        conversation.jobId === selectedJobId
          ? { ...conversation, lastMessage: message, unreadCount: 0 }
          : conversation
      )))
      if (message.senderId !== user.id) {
        markChatRead(selectedJobId, message.id).catch(() => {})
      }
    })
  }, [selectedJobId, subscribe, user.id])

  function selectConversation(jobId) {
    setSelectedJobId(jobId)
    setSearchParams({ jobId: String(jobId) })
  }

  async function loadOlder() {
    if (!selectedJobId || !history?.hasMore || !history.nextBeforeId) return
    setHistoryLoading(true)
    setError('')
    try {
      const older = await getChatHistory(selectedJobId, {
        beforeId: history.nextBeforeId,
        limit: 50,
      })
      setHistory((current) => ({
        messages: [...older.messages, ...(current?.messages ?? [])],
        nextBeforeId: older.nextBeforeId,
        hasMore: older.hasMore,
      }))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać starszych wiadomości.')
    } finally {
      setHistoryLoading(false)
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const content = draft.trim()
    if (!content || !selectedJobId || sending || selectedBlockedByMe) return

    setSending(true)
    setError('')
    const clientMessageId = window.crypto.randomUUID()
    try {
      const sent = await sendChatMessage(selectedJobId, content, clientMessageId)
      setDraft('')
      setHistory((current) => {
        if (!current || current.messages.some((message) => message.id === sent.id)) return current
        return { ...current, messages: [...current.messages, sent] }
      })
      setConversations((current) => current.map((conversation) => (
        conversation.jobId === selectedJobId
          ? { ...conversation, lastMessage: sent }
          : conversation
      )))
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się wysłać wiadomości. Interakcja z tym użytkownikiem może być niedostępna.')
    } finally {
      setSending(false)
    }
  }

  async function handleToggleBlock() {
    if (!selectedConversation || blockUpdating) return
    const otherUserId = selectedConversation.otherUserId

    setBlockUpdating(true)
    setError('')
    try {
      if (selectedBlockedByMe) {
        await unblockUser(otherUserId)
        setBlockedUserIds((current) => {
          const next = new Set(current)
          next.delete(otherUserId)
          return next
        })
      } else {
        await blockUser(otherUserId)
        setBlockedUserIds((current) => new Set(current).add(otherUserId))
        setDraft('')
      }
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się zmienić blokady użytkownika.')
    } finally {
      setBlockUpdating(false)
    }
  }

  const readOnly = selectedConversation
    ? !SENDABLE_STATUSES.has(selectedConversation.jobStatus)
    : true

  return (
    <main className="chat-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Komunikacja</span>
          <h1>Czaty</h1>
          <p>Rozmowy są dostępne wyłącznie dla zlecającego i przypisanego wykonawcy.</p>
        </div>
        <span className={`realtime-status realtime-status--${realtimeStatus}`}>Realtime: {realtimeStatus}</span>
      </header>

      {error && <div className="form-message form-message--error">{error}</div>}
      {loading && <div className="page-state">Pobieranie rozmów…</div>}

      {!loading && conversations.length === 0 && (
        <div className="page-state">Nie masz jeszcze rozmów. Czat pojawi się po przyjęciu zlecenia.</div>
      )}

      {!loading && conversations.length > 0 && (
        <section className="chat-workspace">
          <aside className="chat-sidebar" aria-label="Rozmowy">
            {conversations.map((conversation) => (
              <button
                className={`chat-conversation ${conversation.jobId === selectedJobId ? 'chat-conversation--active' : ''}`}
                key={conversation.jobId}
                type="button"
                onClick={() => selectConversation(conversation.jobId)}
              >
                <div className="chat-conversation__topline">
                  <strong>{conversation.otherUserNickname}</strong>
                  {conversation.unreadCount > 0 && <span className="unread-badge">{conversation.unreadCount}</span>}
                </div>
                <span className="chat-conversation__job">{conversation.jobTitle}</span>
                <span className="chat-conversation__preview">
                  {conversation.lastMessage?.content || 'Brak wiadomości — rozpocznij rozmowę.'}
                </span>
              </button>
            ))}
          </aside>

          <div className="chat-thread">
            {selectedConversation && (
              <header className="chat-thread__header">
                <div className="chat-thread__identity">
                  <strong>{selectedConversation.otherUserNickname}</strong>
                  <span>{selectedConversation.jobTitle}</span>
                </div>
                <div className="chat-thread__actions">
                  <span className={`status-pill status-pill--${selectedConversation.jobStatus.toLowerCase()}`}>
                    {STATUS_LABELS[selectedConversation.jobStatus] || selectedConversation.jobStatus}
                  </span>
                  <button
                    className="button button--secondary chat-block-button"
                    type="button"
                    disabled={blockUpdating}
                    onClick={handleToggleBlock}
                  >
                    {blockUpdating
                      ? 'Zapisywanie…'
                      : selectedBlockedByMe ? 'Odblokuj użytkownika' : 'Zablokuj użytkownika'}
                  </button>
                </div>
              </header>
            )}

            {selectedConversation && (
              <div className="chat-thread__trust">
                <UserTrustCard
                  userId={selectedConversation.otherUserId}
                  roleLabel="Rozmawiasz z"
                  compact
                />
              </div>
            )}

            <div className="chat-thread__messages">
              {history?.hasMore && (
                <button className="chat-load-older" type="button" disabled={historyLoading} onClick={loadOlder}>
                  {historyLoading ? 'Pobieranie…' : 'Pokaż starsze wiadomości'}
                </button>
              )}
              {historyLoading && !history && <div className="page-state">Pobieranie historii…</div>}
              {history && history.messages.length === 0 && <div className="page-state">Brak wiadomości w tej rozmowie.</div>}
              {history?.messages.map((message) => {
                const mine = message.senderId === user.id
                return (
                  <article className={`chat-message ${mine ? 'chat-message--mine' : ''}`} key={message.id}>
                    <div className="chat-message__meta">
                      <strong>{mine ? 'Ty' : message.senderNickname}</strong>
                      <time>{new Date(message.createdAt).toLocaleString('pl-PL')}</time>
                    </div>
                    <p>{message.content}</p>
                  </article>
                )
              })}
            </div>

            {selectedConversation && (
              <form className="chat-composer" onSubmit={handleSubmit}>
                {selectedBlockedByMe ? (
                  <div className="chat-readonly chat-readonly--blocked">
                    Zablokowałeś tego użytkownika. Nowe wiadomości są wyłączone do czasu odblokowania; historia rozmowy pozostaje dostępna.
                  </div>
                ) : readOnly ? (
                  <div className="chat-readonly">To zlecenie jest zamknięte. Historia czatu pozostaje dostępna tylko do odczytu.</div>
                ) : (
                  <>
                    <textarea
                      maxLength={4000}
                      placeholder="Napisz wiadomość…"
                      rows={2}
                      value={draft}
                      onChange={(event) => setDraft(event.target.value)}
                    />
                    <button className="button button--primary" type="submit" disabled={sending || !draft.trim()}>
                      {sending ? 'Wysyłanie…' : 'Wyślij'}
                    </button>
                  </>
                )}
              </form>
            )}
          </div>
        </section>
      )}
    </main>
  )
}

export default ChatPage
