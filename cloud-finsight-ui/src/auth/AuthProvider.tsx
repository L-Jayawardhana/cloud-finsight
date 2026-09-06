import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import keycloak from './keycloak'

interface AuthContextValue {
  initialized: boolean
  authenticated: boolean
  token: string | undefined
  username: string | undefined
  roles: string[]
  login: (redirectUri?: string) => void
  logout: () => void
  hasRole: (role: string) => boolean
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

// Deliberately NOT under /auth/* - nginx proxies that whole prefix to
// Keycloak (see nginx.conf), so a route there would 404 instead of
// reaching this SPA route.
const CALLBACK_PATH = '/sso/callback'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [initialized, setInitialized] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [token, setToken] = useState<string | undefined>(undefined)
  const didInit = useRef(false)

  useEffect(() => {
    if (didInit.current) return
    didInit.current = true

    keycloak.onTokenExpired = () => {
      keycloak
        .updateToken(30)
        .then((refreshed) => {
          if (refreshed) setToken(keycloak.token)
        })
        .catch(() => keycloak.login())
    }

    keycloak
      .init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then((auth) => {
        setAuthenticated(auth)
        setToken(keycloak.token)
        setInitialized(true)
      })
      .catch(() => {
        setInitialized(true)
      })
  }, [])

  const login = (redirectUri?: string) => {
    keycloak.login({
      redirectUri: redirectUri ?? `${window.location.origin}${CALLBACK_PATH}`,
    })
  }

  const logout = () => {
    keycloak.logout({ redirectUri: window.location.origin })
  }

  const hasRole = (role: string) =>
    keycloak.tokenParsed?.realm_access?.roles?.includes(role) ?? false

  const value: AuthContextValue = {
    initialized,
    authenticated,
    token,
    username: keycloak.tokenParsed?.preferred_username,
    roles: keycloak.tokenParsed?.realm_access?.roles ?? [],
    login,
    logout,
    hasRole,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
