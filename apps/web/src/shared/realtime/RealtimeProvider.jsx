import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../features/auth/AuthContext.js'
import { getAccessToken } from '../api/apiClient.js'
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
