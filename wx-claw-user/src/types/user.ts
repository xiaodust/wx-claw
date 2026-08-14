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

export interface AiConfig {
  configured: boolean
  apiKeyMasked: string | null
  baseUrl: string | null
  updatedAt: string | null
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
