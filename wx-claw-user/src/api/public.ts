import axios from 'axios'
import type {
  AuthResult,
  EmailCodeRequest,
  ForgotPasswordRequest,
  LoginRequest,
  OperationResult,
  RegisterTenantRequest,
  RegisterTenantResult,
  ResetPasswordRequest,
} from '../types/user'

/**
 * 公开接口：不附加 API Key 拦截器，直接访问后端 /api/public/*。
 */
export function registerTenant(payload: RegisterTenantRequest): Promise<RegisterTenantResult> {
  return axios.post('/api/public/tenants/register', payload).then(r => r.data)
}

export function loginTenant(payload: LoginRequest): Promise<AuthResult> {
  return axios.post('/api/public/auth/login', payload).then(r => r.data)
}

export function forgotPassword(payload: ForgotPasswordRequest): Promise<OperationResult> {
  return axios.post('/api/public/auth/forgot-password', payload).then(r => r.data)
}

export function resetPassword(payload: ResetPasswordRequest): Promise<OperationResult> {
  return axios.post('/api/public/auth/reset-password', payload).then(r => r.data)
}

export function sendEmailCode(payload: EmailCodeRequest): Promise<OperationResult> {
  return axios.post('/api/public/auth/email-code', payload).then(r => r.data)
}
