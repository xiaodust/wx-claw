import { api } from './client'
import type { AiConfigEntry, AiConfigs, Bot, Conversation, Message, QrInfo } from '../types/user'

export function listBots(): Promise<Bot[]> {
  return api.get('/bots').then(r => r.data)
}

export function createBot(displayName: string): Promise<Bot> {
  return api.post('/bots', { displayName }).then(r => r.data)
}

export function getBot(botId: string): Promise<Bot> {
  return api.get(`/bots/${botId}`).then(r => r.data)
}

export function getQr(botId: string): Promise<QrInfo> {
  return api.get(`/bots/${botId}/qr`).then(r => r.data)
}

export function deleteBot(botId: string): Promise<void> {
  return api.delete(`/bots/${botId}`).then(r => r.data)
}

export function listConversations(botId: string, limit = 20): Promise<Conversation[]> {
  return api.get(`/bots/${botId}/conversations`, { params: { limit } }).then(r => r.data)
}

export function listMessages(botId: string, conversationId: string): Promise<Message[]> {
  return api.get(`/bots/${botId}/conversations/${conversationId}/messages`).then(r => r.data)
}

export function getAiConfigs(): Promise<AiConfigs> {
  return api.get('/ai-config').then(r => r.data)
}

export function saveAiConfig(capability: string, apiKey: string): Promise<AiConfigEntry> {
  return api.put(`/ai-config/${capability}`, { apiKey }).then(r => r.data)
}

export function clearAiConfig(capability: string): Promise<void> {
  return api.delete(`/ai-config/${capability}`).then(r => r.data)
}
