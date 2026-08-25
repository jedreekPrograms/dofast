import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './app/App.jsx'
import AuthProvider from './features/auth/AuthProvider.jsx'
import NotificationProvider from './features/notifications/NotificationProvider.jsx'
import RealtimeProvider from './shared/realtime/RealtimeProvider.jsx'
import './shared/styles/global.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <RealtimeProvider>
        <NotificationProvider>
          <App />
        </NotificationProvider>
      </RealtimeProvider>
    </AuthProvider>
  </StrictMode>,
)
