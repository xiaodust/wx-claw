import { api } from './client'
import type { BotStatus, Conversation, InvocationDetail, InvocationSummary, Message, Overview, PageResult } from '../types/admin'

export const getOverview = (tenantId?:string) => api.get<Overview>('/overview', { params:{ tenantId } }).then(r => r.data)
export const getBots = (params:Record<string, unknown> = {}) => api.get<BotStatus[]>('/bots', { params }).then(r => r.data)
export const getConversations = (params:Record<string, unknown>) => api.get<PageResult<Conversation>>('/conversations', { params }).then(r => r.data)
export const getConversation = (id:string) => api.get<Conversation>(`/conversations/${id}`).then(r => r.data)
export const getMessages = (id:string) => api.get<Message[]>(`/conversations/${id}/messages`).then(r => r.data)
export const getInvocations = (id:string) => api.get<InvocationSummary[]>(`/conversations/${id}/invocations`).then(r => r.data)
export const getInvocation = (id:string) => api.get<InvocationDetail>(`/invocations/${id}`).then(r => r.data)
