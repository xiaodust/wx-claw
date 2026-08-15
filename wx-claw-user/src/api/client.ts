import axios from 'axios'

/** 控制台会话 token（账号密码登录后签发）；后端将其作为 X-API-Key 头识别。 */
export const AUTH_FLAG_STORAGE = 'wx-claw-user-authenticated'

export const api = axios.create({ baseURL: '/api/user', timeout: 30000, withCredentials: true })

api.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401) {
    sessionStorage.removeItem(AUTH_FLAG_STORAGE)
    if (location.pathname !== '/login') location.assign('/login')
  }
  return Promise.reject(error)
})
