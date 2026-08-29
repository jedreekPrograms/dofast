import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AdminDisputesPage from '../features/admin/pages/AdminDisputesPage.jsx'
import AdminJobReportsPage from '../features/admin/pages/AdminJobReportsPage.jsx'
import AdminPage from '../features/admin/pages/AdminPage.jsx'
import AdminPayoutsPage from '../features/admin/pages/AdminPayoutsPage.jsx'
import AdminVerificationsPage from '../features/admin/pages/AdminVerificationsPage.jsx'
import LoginPage from '../features/auth/pages/LoginPage.jsx'
import RegisterPage from '../features/auth/pages/RegisterPage.jsx'
import RequireAdmin from '../features/auth/components/RequireAdmin.jsx'
import RequireAuth from '../features/auth/components/RequireAuth.jsx'
import ChatPage from '../features/chat/pages/ChatPage.jsx'
import DisputesPage from '../features/disputes/pages/DisputesPage.jsx'
import MyJobReportsPage from '../features/jobReports/pages/MyJobReportsPage.jsx'
import CreateJobPage from '../features/jobs/pages/CreateJobPage.jsx'
import JobDetailsPage from '../features/jobs/pages/JobDetailsPage.jsx'
import JobExecutionPage from '../features/jobs/pages/JobExecutionPage.jsx'
import JobPublicationReturnPage from '../features/jobs/pages/JobPublicationReturnPage.jsx'
import JobsPage from '../features/jobs/pages/JobsPage.jsx'
import MyJobsPage from '../features/jobs/pages/MyJobsPage.jsx'
import SavedJobsPage from '../features/jobs/pages/SavedJobsPage.jsx'
import SavedSearchesPage from '../features/jobs/pages/SavedSearchesPage.jsx'
import NotificationsPage from '../features/notifications/pages/NotificationsPage.jsx'
import ProfilePage from '../features/profile/pages/ProfilePage.jsx'
import PublicProfilePage from '../features/reviews/pages/PublicProfilePage.jsx'
import BlockedUsersPage from '../features/userBlocks/pages/BlockedUsersPage.jsx'
import VerificationPage from '../features/verification/pages/VerificationPage.jsx'
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
          <Route path="/users/:userId" element={<PublicProfilePage />} />

          <Route element={<RequireAuth />}>
            <Route path="/my-jobs" element={<MyJobsPage />} />
            <Route path="/saved-jobs" element={<SavedJobsPage />} />
            <Route path="/saved-searches" element={<SavedSearchesPage />} />
            <Route path="/my-reports" element={<MyJobReportsPage />} />
            <Route path="/blocked-users" element={<BlockedUsersPage />} />
            <Route path="/jobs/new" element={<CreateJobPage />} />
            <Route path="/jobs/publications/:publicationId/return" element={<JobPublicationReturnPage />} />
            <Route path="/jobs/:jobId" element={<JobDetailsPage />} />
            <Route path="/jobs/:jobId/route" element={<JobExecutionPage />} />
            <Route path="/chat" element={<ChatPage />} />
            <Route path="/disputes" element={<DisputesPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/wallet" element={<WalletPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/verification" element={<VerificationPage />} />

            <Route element={<RequireAdmin />}>
              <Route path="/admin" element={<AdminPage />} />
              <Route path="/admin/disputes" element={<AdminDisputesPage />} />
              <Route path="/admin/job-reports" element={<AdminJobReportsPage />} />
              <Route path="/admin/payouts" element={<AdminPayoutsPage />} />
              <Route path="/admin/verifications" element={<AdminVerificationsPage />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
