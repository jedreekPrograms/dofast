import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth/AuthContext.js'
import { useRealtime } from '../../shared/realtime/RealtimeContext.js'
import { getUnreadNotificationCount } from './api/notificationsApi.js'
import { NotificationContext } from './NotificationContext.js'

function NotificationProvider({ children }) {
  const { user } = useAuth()
  const { subscribe } = useRealtime()
  const [unreadCount, setUnreadCount] = useState(0)
  const [lastNotification, setLastNotification] = useState(null)

  const refreshUnreadCount = useCallback(async () => {
    if (!user) return
    try {
      const response = await getUnreadNotificationCount()
      setUnreadCount(response.unreadCount ?? 0)
    } catch {
      // A temporary notification failure must not break the main application shell.
    }
  }, [user])

  useEffect(() => {
    if (!user) return undefined

    let active = true
    getUnreadNotificationCount()
      .then((response) => {
        if (active) setUnreadCount(response.unreadCount ?? 0)
      })
      .catch(() => {})

    const interval = window.setInterval(refreshUnreadCount, 30000)
    return () => {
      active = false
      window.clearInterval(interval)
    }
  }, [refreshUnreadCount, user])

  useEffect(() => {
    if (!user) return undefined
    return subscribe('/user/queue/notifications', (notification) => {
      setLastNotification(notification)
      if (!notification.read) {
        setUnreadCount((current) => current + 1)
      }
    })
  }, [subscribe, user])

  const value = useMemo(() => ({
    unreadCount: user ? unreadCount : 0,
    lastNotification: user ? lastNotification : null,
    refreshUnreadCount,
  }), [lastNotification, refreshUnreadCount, unreadCount, user])

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  )
}

export default NotificationProvider
