import { BrowserRouter, Routes, Route } from 'react-router-dom'
import JobsPage from './pages/JobsPage'

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