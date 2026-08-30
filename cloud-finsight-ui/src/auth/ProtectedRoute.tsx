import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { useAuth } from './AuthProvider'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { initialized, authenticated, login } = useAuth()

  useEffect(() => {
    if (initialized && !authenticated) {
      login()
    }
  }, [initialized, authenticated, login])

  if (!initialized || !authenticated) {
    return (
      <div className="full-page-loader">
        <div className="spinner" />
        <p>Redirecting to sign-in…</p>
      </div>
    )
  }

  return <>{children}</>
}
