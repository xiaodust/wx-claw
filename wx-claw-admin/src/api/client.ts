import axios from 'axios'

export const API_KEY_STORAGE = 'wx-claw-admin-api-key'

export const api = axios.create({ baseURL: '/api/admin', timeout: 30000 })

api.interceptors.request.use(config => {
  const key = sessionStorage.getItem(API_KEY_STORAGE)
  if (key) config.headers['X-API-Key'] = key
  return config
})

api.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401) {
    sessionStorage.removeItem(API_KEY_STORAGE)
    if (location.pathname !== '/login') location.assign('/login')
  }
  return Promise.reject(error)
})
