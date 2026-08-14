<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBot, listConversations, listMessages } from '../api/user'
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

const STATUS_TAG: Record<string, string> = {
  ONLINE: 'success',
  WAITING_QR: 'warning',
  RECONNECTING: 'warning',
  ERROR: 'danger',
  STARTING: 'info',
  OFFLINE: 'info',
  STOPPED: 'info',
  UNKNOWN: 'info',
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
    <div class="toolbar">
      <el-button @click="router.push('/bots')">← 返回</el-button>
      <el-button :loading="loadingConv" @click="refreshConversations">刷新会话</el-button>
    </div>

    <h1 class="page-title">{{ bot?.displayName || botId }}</h1>
    <p class="page-subtitle mono">{{ botId }}</p>

    <div v-if="bot" class="panel info-panel">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="运行状态">
          <el-tag :type="(STATUS_TAG[bot.runtimeStatus || 'UNKNOWN'] as any)">{{ bot.runtimeStatus || 'UNKNOWN' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="配置状态">{{ bot.configuredStatus }}</el-descriptions-item>
        <el-descriptions-item label="连接时间">{{ formatTime(bot.connectedAt) }}</el-descriptions-item>
        <el-descriptions-item label="最后拉取">{{ formatTime(bot.lastPollAt) }}</el-descriptions-item>
        <el-descriptions-item label="最后消息">{{ formatTime(bot.lastMessageAt) }}</el-descriptions-item>
        <el-descriptions-item label="最近错误">{{ bot.lastError || '—' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="chat-layout">
      <div class="panel conv-panel">
        <h3>会话列表</h3>
        <el-empty v-if="!conversations.length" description="暂无会话" :image-size="60" />
        <div
          v-for="c in conversations"
          :key="c.id"
          class="conv-item"
          :class="{ active: selected?.id === c.id }"
          @click="selectConversation(c)"
        >
          <div class="conv-title">
            <span class="mono">{{ c.sessionId }}</span>
            <el-tag v-if="c.active" size="small" type="success">活跃</el-tag>
            <el-tag v-else size="small" type="info">已关闭</el-tag>
          </div>
          <div class="muted conv-meta">{{ c.messageCount }} 条 · {{ formatTime(c.lastMessageTime) }}</div>
        </div>
      </div>

      <div class="panel msg-panel">
        <h3>聊天记录</h3>
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
              <div class="muted bubble-time">{{ formatTime(m.createTime) }} · seq {{ m.messageSeq }}</div>
            </div>
          </div>
          <el-empty v-if="!messages.length" description="暂无消息" :image-size="60" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.info-panel { margin-bottom: 18px; }
.chat-layout { display: flex; gap: 16px; align-items: flex-start; }
.conv-panel { width: 320px; flex-shrink: 0; max-height: 620px; overflow: auto; }
.msg-panel { flex: 1; min-width: 0; max-height: 620px; display: flex; flex-direction: column; }
.conv-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; margin-bottom: 6px; }
.conv-item:hover { background: #f5f7fa; }
.conv-item.active { background: #ecf5ff; }
.conv-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-size: 13px; }
.conv-meta { font-size: 12px; margin-top: 4px; }
.msg-list { flex: 1; overflow: auto; padding: 8px 4px; }
.msg-row { display: flex; margin-bottom: 12px; }
.msg-row.from-user { justify-content: flex-end; }
.msg-row.from-bot { justify-content: flex-start; }
.bubble { max-width: 78%; background: #f2f4f7; border-radius: 10px; padding: 10px 12px; }
.from-user .bubble { background: #d9ecff; }
.bubble-text { white-space: pre-wrap; word-break: break-word; font-size: 14px; line-height: 1.55; }
.bubble-time { font-size: 11px; margin-top: 6px; }
.msg-placeholder { color: #8492a6; text-align: center; padding: 60px 0; }
</style>
