import axios from 'axios'
import type {
  AuthResult,
  EmailCodeRequest,
  ForgotPasswordRequest,
  LoginRequest,
  OperationResult,
  AccountInfo,
  RegisterTenantRequest,
  RegisterTenantResult,
  ResetPasswordRequest,
  SetupAccountRequest,
  SetupAccountResult,
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

/** 用 API Key 探测租户账号状态（仅激活流程使用，Key 不落 storage）。 */
export function probeAccount(apiKey: string): Promise<AccountInfo> {
  return axios.get('/api/user/account', { headers: { 'X-API-Key': apiKey } }).then(r => r.data)
}

/** 用 API Key 为无账号租户创建控制台账号（激活流程，Key 不落 storage）。 */
export function activateAccount(apiKey: string, payload: SetupAccountRequest): Promise<SetupAccountResult> {
  return axios.post('/api/user/account/setup', payload, { headers: { 'X-API-Key': apiKey } }).then(r => r.data)
}
