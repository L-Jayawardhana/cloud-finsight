import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AuthCallback } from './pages/AuthCallback'
import { ChatPage } from './pages/ChatPage'
import { Dashboard } from './pages/Dashboard'
import { RecommendationDetail } from './pages/RecommendationDetail'
import { RecommendationsPage } from './pages/RecommendationsPage'

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
        path="/recommendations"
        element={
          <ProtectedRoute>
            <RecommendationsPage />
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
      <Route
        path="/chat"
        element={
          <ProtectedRoute>
            <ChatPage />
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

export default App
