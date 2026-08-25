import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useNotifications } from '../NotificationContext.js'
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notificationsApi.js'
import './NotificationsPage.css'

const TYPE_LABELS = {
  JOB_ACCEPTED: 'Zlecenie',
  COMPLETION_REQUESTED: 'Zlecenie',
  JOB_COMPLETED: 'Rozliczenie',
  JOB_CANCELLED: 'Zlecenie',
  DISPUTE_OPENED: 'Spór',
  DISPUTE_CLAIMED: 'Spór',
  DISPUTE_RESOLVED: 'Spór',
  CHAT_MESSAGE: 'Czat',
}

function notificationTarget(notification) {
  if (notification.type === 'CHAT_MESSAGE' && notification.jobId) {
    return `/chat?jobId=${notification.jobId}`
  }
  if (notification.disputeId) return '/disputes'
  if (notification.jobId) return '/my-jobs'
  return null
}

function NotificationsPage() {
  const { lastNotification, refreshUnreadCount } = useNotifications()
  const [notifications, setNotifications] = useState([])
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const loadNotifications = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await getNotifications({ unreadOnly, page: 0, size: 50 })
      setNotifications(response.content ?? [])
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się pobrać powiadomień.')
    } finally {
      setLoading(false)
    }
  }, [unreadOnly])

  useEffect(() => {
    loadNotifications()
  }, [loadNotifications])

  useEffect(() => {
    if (!lastNotification) return
    setNotifications((current) => {
      if (unreadOnly && lastNotification.read) return current
      if (current.some((notification) => notification.id === lastNotification.id)) return current
      return [lastNotification, ...current]
    })
  }, [lastNotification, unreadOnly])

  const unreadVisible = useMemo(
    () => notifications.filter((notification) => !notification.read).length,
    [notifications],
  )

  async function markRead(notification) {
    if (notification.read) return
    try {
      const updated = await markNotificationRead(notification.id)
      setNotifications((current) => current
        .map((item) => item.id === updated.id ? updated : item)
        .filter((item) => !unreadOnly || !item.read))
      await refreshUnreadCount()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się oznaczyć powiadomienia jako przeczytane.')
    }
  }

  async function markAllRead() {
    setBusy(true)
    setError('')
    try {
      await markAllNotificationsRead()
      if (unreadOnly) {
        setNotifications([])
      } else {
        setNotifications((current) => current.map((item) => ({
          ...item,
          read: true,
          readAt: item.readAt || new Date().toISOString(),
        })))
      }
      await refreshUnreadCount()
    } catch (requestError) {
      setError(requestError.message || 'Nie udało się oznaczyć powiadomień jako przeczytane.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="notifications-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Centrum aktywności</span>
          <h1>Powiadomienia</h1>
          <p>Wiadomości o zleceniach, czatach, rozliczeniach i sporach w jednym miejscu.</p>
        </div>
        <button className="button button--secondary" type="button" disabled={busy || unreadVisible === 0} onClick={markAllRead}>
          Oznacz wszystkie jako przeczytane
        </button>
      </header>

      <div className="segmented-control" role="tablist" aria-label="Filtr powiadomień">
        <button className={!unreadOnly ? 'is-active' : ''} type="button" onClick={() => setUnreadOnly(false)}>Wszystkie</button>
        <button className={unreadOnly ? 'is-active' : ''} type="button" onClick={() => setUnreadOnly(true)}>Nieprzeczytane</button>
      </div>

      {error && <div className="form-message form-message--error">{error}</div>}
      {loading && <div className="page-state">Pobieranie powiadomień…</div>}
      {!loading && notifications.length === 0 && <div className="page-state">Brak powiadomień w tej sekcji.</div>}

      {!loading && notifications.length > 0 && (
        <section className="notification-list" aria-label="Lista powiadomień">
          {notifications.map((notification) => {
            const target = notificationTarget(notification)
            return (
              <article className={`notification-card ${notification.read ? '' : 'notification-card--unread'}`} key={notification.id}>
                <button className="notification-card__body" type="button" onClick={() => markRead(notification)}>
                  <div className="notification-card__meta">
                    <span>{TYPE_LABELS[notification.type] || notification.type}</span>
                    <time>{new Date(notification.createdAt).toLocaleString('pl-PL')}</time>
                  </div>
                  <h2>{notification.title}</h2>
                  <p>{notification.body}</p>
                </button>
                {target && <Link className="button button--secondary" to={target} onClick={() => markRead(notification)}>Otwórz</Link>}
              </article>
            )
          })}
        </section>
      )}
    </main>
  )
}

export default NotificationsPage
