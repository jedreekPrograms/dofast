import { createContext, useContext } from 'react'

export const RealtimeContext = createContext(null)

export function useRealtime() {
  const context = useContext(RealtimeContext)
  if (!context) {
    throw new Error('useRealtime must be used inside RealtimeProvider')
  }
  return context
}
