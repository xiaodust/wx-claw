import axios from 'axios'

export const AUTH_FLAG_STORAGE = 'wx-claw-admin-authenticated'

let apiKey = ''

export function setApiKey(value: string) { apiKey = value }
export function clearApiKey() { apiKey = '' }

export const api = axios.create({ baseURL: '/api/admin', timeout: 30000, withCredentials: true })

api.interceptors.request.use(config => {
  if (apiKey) config.headers['X-API-Key'] = apiKey
  return config
})

api.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401) {
    clearApiKey()
    sessionStorage.removeItem(AUTH_FLAG_STORAGE)
    if (location.pathname !== '/login') location.assign('/login')
  }
  return Promise.reject(error)
})
