import axios from 'axios'
import type { AdminLoginResult } from '../types/admin'

/** 管理端账号密码登录（公开接口，不需要 X-API-Key）。 */
export function adminLogin(username: string, password: string): Promise<AdminLoginResult> {
  return axios.post('/api/public/auth/admin-login', { username, password }).then(r => r.data)
}
