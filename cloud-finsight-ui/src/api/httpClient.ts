import axios from 'axios'
import keycloak from '../auth/keycloak'

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

httpClient.interceptors.request.use(async (config) => {
  if (keycloak.token) {
    try {
      await keycloak.updateToken(30)
    } catch {
      keycloak.login()
      return Promise.reject(new Error('Session expired, redirecting to login'))
    }
    config.headers.Authorization = `Bearer ${keycloak.token}`
  }
  return config
})
