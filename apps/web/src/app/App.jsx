import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AdminDisputesPage from '../features/admin/pages/AdminDisputesPage.jsx'
import AdminPage from '../features/admin/pages/AdminPage.jsx'
import LoginPage from '../features/auth/pages/LoginPage.jsx'
import RegisterPage from '../features/auth/pages/RegisterPage.jsx'
import RequireAdmin from '../features/auth/components/RequireAdmin.jsx'
import RequireAuth from '../features/auth/components/RequireAuth.jsx'
import ChatPage from '../features/chat/pages/ChatPage.jsx'
import DisputesPage from '../features/disputes/pages/DisputesPage.jsx'
import CreateJobPage from '../features/jobs/pages/CreateJobPage.jsx'
import JobsPage from '../features/jobs/pages/JobsPage.jsx'
import MyJobsPage from '../features/jobs/pages/MyJobsPage.jsx'
import NotificationsPage from '../features/notifications/pages/NotificationsPage.jsx'
import ProfilePage from '../features/profile/pages/ProfilePage.jsx'
import WalletPage from '../features/wallet/pages/WalletPage.jsx'
import AppShell from '../shared/components/AppShell.jsx'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<JobsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route element={<RequireAuth />}>
            <Route path="/my-jobs" element={<MyJobsPage />} />
            <Route path="/jobs/new" element={<CreateJobPage />} />
            <Route path="/chat" element={<ChatPage />} />
            <Route path="/disputes" element={<DisputesPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/wallet" element={<WalletPage />} />
            <Route path="/profile" element={<ProfilePage />} />

            <Route element={<RequireAdmin />}>
              <Route path="/admin" element={<AdminPage />} />
              <Route path="/admin/disputes" element={<AdminDisputesPage />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
