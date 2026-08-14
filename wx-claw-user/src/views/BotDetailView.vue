<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteBot, getBot, listConversations, listMessages } from '../api/user'
import type { Bot, Conversation, Message } from '../types/user'

const route = useRoute()
const router = useRouter()
const botId = route.params.botId as string

const bot = ref<Bot | null>(null)
const conversations = ref<Conversation[]>([])
const selected = ref<Conversation | null>(null)
const messages = ref<Message[]>([])
const loadingBot = ref(false)
const loadingConv = ref(false)
const loadingMsg = ref(false)
const timer = ref<number | null>(null)

const STATUS_META: Record<string, { tone: string; label: string }> = {
  ONLINE: { tone: 'ok', label: '在线' },
  WAITING_QR: { tone: 'warn', label: '等待扫码' },
  RECONNECTING: { tone: 'warn', label: '重连中' },
  ERROR: { tone: 'bad', label: '异常' },
  STARTING: { tone: 'info', label: '启动中' },
  OFFLINE: { tone: 'info', label: '离线' },
  STOPPED: { tone: 'info', label: '已停止' },
  UNKNOWN: { tone: 'info', label: '未知' },
}

function statusMeta(status: string | null | undefined) {
  return STATUS_META[status || 'UNKNOWN'] || STATUS_META.UNKNOWN
}

async function refreshBot() {
  loadingBot.value = true
  try {
    bot.value = await getBot(botId)
  } finally {
    loadingBot.value = false
  }
}

async function refreshConversations() {
  loadingConv.value = true
  try {
    conversations.value = await listConversations(botId)
    if (selected.value) {
      const still = conversations.value.find(c => c.id === selected.value!.id)
      if (!still) selected.value = null
    }
    if (!selected.value && conversations.value.length > 0) {
      selected.value = conversations.value[0]
    }
  } finally {
    loadingConv.value = false
  }
}

async function refreshMessages() {
  if (!selected.value) return
  loadingMsg.value = true
  try {
    messages.value = await listMessages(botId, selected.value.id)
  } finally {
    loadingMsg.value = false
  }
}

async function selectConversation(row: Conversation) {
  selected.value = row
  await refreshMessages()
}

async function removeBot() {
  try {
    await ElMessageBox.confirm(`确认删除 Bot「${bot.value?.displayName || botId}」？删除后将从列表移除，需要重新创建并扫码连接。`, '删除 Bot', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteBot(botId)
    ElMessage.success('Bot 已删除')
    router.push('/bots')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

function formatTime(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

watch(() => selected.value?.id, async id => {
  if (id) await refreshMessages()
})

onMounted(async () => {
  await refreshBot()
  await refreshConversations()
  if (selected.value) await refreshMessages()
  timer.value = window.setInterval(async () => {
    await refreshBot()
    if (selected.value) await refreshMessages()
  }, 5000)
})

onBeforeUnmount(() => {
  if (timer.value !== null) window.clearInterval(timer.value)
})
</script>

<template>
  <div>
    <div class="detail-head">
      <el-button @click="router.push('/bots')">← 返回列表</el-button>
      <div class="head-actions">
        <el-button :loading="loadingConv" @click="refreshConversations">刷新会话</el-button>
        <el-button type="danger" plain @click="removeBot">删除 Bot</el-button>
      </div>
    </div>

    <p class="page-kicker">BOT DETAIL</p>
    <div class="title-row">
      <h1 class="page-title">{{ bot?.displayName || botId }}</h1>
      <span v-if="bot" class="status-pill" :class="`tone-${statusMeta(bot.runtimeStatus).tone}`">
        <span class="status-dot"></span>{{ statusMeta(bot.runtimeStatus).label }}
      </span>
    </div>
    <p class="page-subtitle mono">{{ botId }}</p>

    <div v-if="bot" class="stat-grid">
      <div class="stat-tile">
        <span class="stat-label">配置状态</span>
        <span class="stat-value">{{ bot.configuredStatus }}</span>
      </div>
      <div class="stat-tile">
        <span class="stat-label">连接时间</span>
        <span class="stat-value mono">{{ formatTime(bot.connectedAt) }}</span>
      </div>
      <div class="stat-tile">
        <span class="stat-label">最后拉取</span>
        <span class="stat-value mono">{{ formatTime(bot.lastPollAt) }}</span>
      </div>
      <div class="stat-tile">
        <span class="stat-label">最后消息</span>
        <span class="stat-value mono">{{ formatTime(bot.lastMessageAt) }}</span>
      </div>
      <div class="stat-tile stat-wide">
        <span class="stat-label">最近错误</span>
        <span class="stat-value" :class="{ 'err': bot.lastError }">{{ bot.lastError || '无' }}</span>
      </div>
    </div>

    <div class="chat-layout">
      <div class="panel conv-panel">
        <h3 class="panel-title">会话列表 <span class="mono count">{{ conversations.length.toString().padStart(2, '0') }}</span></h3>
        <el-empty v-if="!conversations.length" description="暂无会话" :image-size="60" />
        <div
          v-for="c in conversations"
          :key="c.id"
          class="conv-item"
          :class="{ active: selected?.id === c.id }"
          @click="selectConversation(c)"
        >
          <div class="conv-title">
            <span class="mono session-id">{{ c.sessionId }}</span>
            <span class="conv-state" :class="c.active ? 'live' : 'closed'">{{ c.active ? '活跃' : '已关闭' }}</span>
          </div>
          <div class="muted conv-meta mono">{{ c.messageCount }} 条 · {{ formatTime(c.lastMessageTime) }}</div>
        </div>
      </div>

      <div class="panel msg-panel">
        <h3 class="panel-title">聊天记录</h3>
        <div v-if="!selected" class="msg-placeholder">选择左侧会话查看聊天记录</div>
        <div v-else v-loading="loadingMsg" class="msg-list">
          <div
            v-for="m in messages"
            :key="m.id"
            class="msg-row"
            :class="m.messageType === 0 ? 'from-user' : 'from-bot'"
          >
            <div class="bubble">
              <div class="bubble-text">{{ m.content || '(空)' }}</div>
              <div class="muted bubble-time mono">{{ formatTime(m.createTime) }} · seq {{ m.messageSeq }}</div>
            </div>
          </div>
          <el-empty v-if="!messages.length" description="暂无消息" :image-size="60" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.head-actions { display: flex; gap: 10px; }
.title-row { display: flex; align-items: center; gap: 14px; }
.title-row .page-title { margin-bottom: 0; }

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 3px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid transparent;
}
.status-dot { width: 6px; height: 6px; border-radius: 50%; }
.tone-ok { color: var(--accent-2); border-color: rgba(45, 225, 194, 0.35); background: rgba(45, 225, 194, 0.08); }
.tone-ok .status-dot { background: var(--accent-2); box-shadow: 0 0 8px var(--accent-2); animation: pulse 1.8s ease-in-out infinite; }
.tone-warn { color: var(--accent); border-color: rgba(255, 180, 0, 0.35); background: rgba(255, 180, 0, 0.08); }
.tone-warn .status-dot { background: var(--accent); box-shadow: 0 0 8px var(--accent); }
.tone-bad { color: var(--danger); border-color: rgba(255, 107, 107, 0.4); background: rgba(255, 107, 107, 0.08); }
.tone-bad .status-dot { background: var(--danger); box-shadow: 0 0 8px var(--danger); }
.tone-info { color: var(--muted); border-color: var(--line); background: rgba(255, 255, 255, 0.03); }
.tone-info .status-dot { background: #8a94a6; }

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.82); }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}
.stat-tile {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.025), transparent 34%), var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 16px 18px;
  position: relative;
  overflow: hidden;
}
.stat-tile::before {
  content: "";
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--accent), transparent 70%);
}
.stat-wide { grid-column: span 2; }
.stat-label { display: block; font-size: 11px; color: var(--muted); letter-spacing: 1px; margin-bottom: 8px; }
.stat-value { font-size: 14px; font-weight: 700; word-break: break-all; }
.stat-value.err { color: var(--danger); font-weight: 500; font-size: 13px; }

.chat-layout { display: flex; gap: 16px; align-items: stretch; }
.panel-title { margin: 0 0 12px; font-size: 14px; letter-spacing: 1px; display: flex; align-items: center; gap: 8px; }
.panel-title .count { color: var(--accent-2); font-size: 11px; }
.conv-panel { width: 320px; flex-shrink: 0; max-height: 640px; overflow: auto; }
.msg-panel { flex: 1; min-width: 0; max-height: 640px; display: flex; flex-direction: column; }
.conv-item {
  padding: 11px 13px;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 6px;
  border: 1px solid transparent;
  border-left: 3px solid transparent;
  transition: background 160ms, border-color 160ms;
}
.conv-item:hover { background: rgba(255, 255, 255, 0.04); }
.conv-item.active { background: rgba(255, 180, 0, 0.07); border-color: var(--line); border-left-color: var(--accent); }
.conv-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-size: 12px; }
.session-id { color: var(--fg); font-size: 11px; word-break: break-all; }
.conv-state { font-size: 11px; font-weight: 700; flex-shrink: 0; }
.conv-state.live { color: var(--accent-2); }
.conv-state.closed { color: var(--muted); }
.conv-meta { font-size: 11px; margin-top: 5px; }

.msg-list { flex: 1; overflow: auto; padding: 8px 4px; }
.msg-row { display: flex; margin-bottom: 12px; }
.msg-row.from-user { justify-content: flex-end; }
.msg-row.from-bot { justify-content: flex-start; }
.bubble {
  max-width: 78%;
  border-radius: 12px;
  padding: 10px 13px;
  border: 1px solid var(--line);
  background: var(--panel-2);
}
.from-bot .bubble { border-left: 3px solid var(--accent-2); }
.from-user .bubble { border-left: 3px solid var(--accent); background: rgba(255, 180, 0, 0.08); }
.bubble-text { white-space: pre-wrap; word-break: break-word; font-size: 14px; line-height: 1.55; }
.bubble-time { font-size: 11px; margin-top: 6px; }
.msg-placeholder { color: var(--muted); text-align: center; padding: 60px 0; }

@media (max-width: 900px) {
  .chat-layout { flex-direction: column; }
  .conv-panel { width: 100%; max-height: 300px; }
  .stat-grid { grid-template-columns: 1fr 1fr; }
  .stat-wide { grid-column: span 2; }
}
@media (max-width: 560px) {
  .stat-grid { grid-template-columns: 1fr; }
  .stat-wide { grid-column: span 1; }
}
</style>
