import { BrowserRouter, Route, Routes } from 'react-router-dom'
import JobsPage from '../features/jobs/pages/JobsPage.jsx'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<JobsPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
