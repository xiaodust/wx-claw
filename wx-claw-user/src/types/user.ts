export interface Bot {
  tenantId: string
  botId: string
  displayName: string
  configuredStatus: string
  runtimeStatus: string | null
  connectedAt: string | null
  statusChangedAt: string | null
  lastPollAt: string | null
  lastMessageAt: string | null
  lastError: string | null
  reconnectAttempts: number
  qrAvailable: boolean
}

export interface QrInfo {
  botId: string
  qrImage: string | null
  status: string
  statusChangedAt: string | null
}

export interface AiConfigEntry {
  configured: boolean
  apiKeyMasked: string | null
  provider: string
  model: string | null
}

export interface AiConfigs {
  chat: AiConfigEntry
  image: AiConfigEntry
  video: AiConfigEntry
  videoDashscope: AiConfigEntry
  tts: AiConfigEntry
  search: AiConfigEntry
}

export interface ModelOption {
  name: string
  free: boolean
}

export interface ChatProviderOption {
  id: string
  name: string
  baseUrl: string
  models: ModelOption[]
}

export interface MediaProviderOption {
  id: string
  name: string
  baseUrl: string
  models: ModelOption[]
}

export interface ModelCatalog {
  chatProviders: ChatProviderOption[]
  imageProviders: MediaProviderOption[]
  videoProviders: MediaProviderOption[]
}

export interface Conversation {
  id: string
  sessionId: string
  botId: string
  active: boolean
  messageCount: number
  lastMessageTime: string | null
  createdTime: string | null
  updatedTime: string | null
}

export interface Message {
  id: string
  messageType: number
  content: string | null
  reasoningContent: string | null
  messageSeq: number
  responseTime: number | null
  errorMsg: string | null
  createTime: string | null
}

export interface RegisterTenantRequest {
  tenantName: string
  tenantCode?: string
  contactEmail: string
  username: string
  password: string
  inviteCode: string
  emailCode: string
}

export interface EmailCodeRequest {
  email: string
  purpose?: string
}

export interface RegisterTenantResult {
  tenantId: string
  tenantCode: string
  tenantName: string
  status: string
  createdAt: string | null
  username: string | null
  sessionToken: string | null
  sessionExpiresAt: string | null
}

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthResult {
  sessionToken: string
  expiresAt: string
  tenantId: string
  tenantCode: string
  tenantName: string
}

export interface ForgotPasswordRequest {
  usernameOrEmail: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface OperationResult {
  message: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface AccountInfo {
  username: string | null
  contactEmail: string | null
  hasAccount: boolean
}

export interface SetupAccountRequest {
  username: string
  contactEmail: string
  emailCode: string
  password: string
}

export interface SetupAccountResult {
  username: string
  sessionToken: string
  sessionExpiresAt: string
}

export interface MailConfig {
  smtpHost: string | null
  smtpPort: number
  username: string | null
  fromAddress: string | null
  enabled: boolean
  configured: boolean
}

export interface SaveMailConfigRequest {
  smtpHost: string
  smtpPort: number
  username: string
  password: string
  fromAddress: string
}
