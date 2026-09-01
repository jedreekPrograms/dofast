import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../features/auth/AuthContext.js'
import {
  getAccessToken,
  refreshAccessToken,
  subscribeAccessToken,
} from '../api/apiClient.js'
import { RealtimeContext } from './RealtimeContext.js'
import { StompClient } from './stompClient.js'

function RealtimeProvider({ children }) {
  const { user } = useAuth()
  const [status, setStatus] = useState('disconnected')

  const client = useMemo(() => {
    const token = user ? getAccessToken() : null
    return token ? new StompClient(token, setStatus) : null
  }, [user])

  useEffect(() => {
    if (!client) return undefined
    let refreshTimer = null

    const refreshForExpiry = (expectedToken, expiresAt) => {
      refreshTimer = null
      refreshAccessToken().catch(() => {
        if (getAccessToken() !== expectedToken) return
        const remainingMs = expiresAt - Date.now()
        if (remainingMs <= 0) {
          client.updateToken(null)
          return
        }
        refreshTimer = window.setTimeout(
          () => refreshForExpiry(expectedToken, expiresAt),
          Math.min(5000, remainingMs),
        )
      })
    }

    const synchronizeAccess = (token, expiresAt) => {
      client.updateToken(token)
      window.clearTimeout(refreshTimer)
      refreshTimer = null

      if (!token || !expiresAt) return
      const remainingMs = Math.max(0, expiresAt - Date.now())
      const refreshLeadMs = Math.min(30000, Math.max(5000, Math.floor(remainingMs / 10)))
      refreshTimer = window.setTimeout(
        () => refreshForExpiry(token, expiresAt),
        Math.max(0, remainingMs - refreshLeadMs),
      )
    }

    const unsubscribe = subscribeAccessToken(synchronizeAccess)
    return () => {
      unsubscribe()
      window.clearTimeout(refreshTimer)
    }
  }, [client])

  useEffect(() => {
    if (!client) return undefined

    client.connect()
    return () => client.disconnect()
  }, [client])

  const subscribe = useCallback((destination, callback) => {
    if (!client) return () => {}
    return client.subscribe(destination, callback)
  }, [client])

  const send = useCallback((destination, payload) => (
    client ? client.send(destination, payload) : false
  ), [client])

  const effectiveStatus = client ? status : 'disconnected'
  const value = useMemo(
    () => ({ status: effectiveStatus, subscribe, send }),
    [effectiveStatus, send, subscribe],
  )

  return (
    <RealtimeContext.Provider value={value}>
      {children}
    </RealtimeContext.Provider>
  )
}

export default RealtimeProvider
