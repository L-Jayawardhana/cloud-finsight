import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AuthCallback } from './pages/AuthCallback'
import { Dashboard } from './pages/Dashboard'
import { RecommendationDetail } from './pages/RecommendationDetail'

function App() {
  return (
    <Routes>
      <Route path="/auth/callback" element={<AuthCallback />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
        }
      />
      <Route
        path="/recommendations/:id"
        element={
          <ProtectedRoute>
            <RecommendationDetail />
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

export default App
