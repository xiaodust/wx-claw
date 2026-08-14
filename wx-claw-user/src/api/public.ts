import axios from 'axios'
import type { AuthResult, LoginRequest, RegisterTenantRequest, RegisterTenantResult } from '../types/user'

/**
 * 公开接口：不附加 API Key 拦截器，直接访问后端 /api/public/*。
 */
export function registerTenant(payload: RegisterTenantRequest): Promise<RegisterTenantResult> {
  return axios.post('/api/public/tenants/register', payload).then(r => r.data)
}

export function loginTenant(payload: LoginRequest): Promise<AuthResult> {
  return axios.post('/api/public/auth/login', payload).then(r => r.data)
}
