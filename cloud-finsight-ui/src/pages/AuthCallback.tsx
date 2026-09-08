import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

export function AuthCallback() {
  const { initialized, authenticated } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!initialized) return
    navigate(authenticated ? '/dashboard' : '/', { replace: true })
  }, [initialized, authenticated, navigate])

  return (
    <div className="full-page-loader">
      <div className="spinner" />
      <p>Completing sign-in…</p>
    </div>
  )
}
