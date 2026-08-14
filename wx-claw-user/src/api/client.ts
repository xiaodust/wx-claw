import axios from 'axios'

/** 控制台会话 token（账号密码登录后签发）；后端将其作为 X-API-Key 头识别。 */
export const AUTH_TOKEN_STORAGE = 'wx-claw-user-token'

export const api = axios.create({ baseURL: '/api/user', timeout: 30000 })

api.interceptors.request.use(config => {
  const key = sessionStorage.getItem(AUTH_TOKEN_STORAGE)
  if (key) config.headers['X-API-Key'] = key
  return config
})

api.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401) {
    sessionStorage.removeItem(AUTH_TOKEN_STORAGE)
    if (location.pathname !== '/login') location.assign('/login')
  }
  return Promise.reject(error)
})
