<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import { createBot, deleteBot, getQr, listBots } from '../api/user'
import type { Bot, QrInfo } from '../types/user'

const router = useRouter()
const bots = ref<Bot[]>([])
const loading = ref(false)
const createVisible = ref(false)
const createName = ref('')
const creating = ref(false)

const qrVisible = ref(false)
const qrBot = ref<Bot | null>(null)
const qrInfo = ref<QrInfo | null>(null)
const qrDataUrl = ref<string | null>(null)
const qrTimer = ref<number | null>(null)

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

async function refresh() {
  loading.value = true
  try {
    bots.value = await listBots()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createName.value = ''
  createVisible.value = true
}

async function submitCreate() {
  creating.value = true
  try {
    const bot = await createBot(createName.value)
    createVisible.value = false
    ElMessage.success('Bot 已创建')
    openQr(bot)
    refresh().catch(() => { /* 列表刷新失败不阻塞二维码弹窗 */ })
  } catch (e: any) {
    const status = e?.response?.status
    const serverMsg = e?.response?.data?.message
    const msg = serverMsg || e?.message || '未知错误'
    const hint = status === 403 && serverMsg?.includes('Missing required scope')
      ? '（登录的 API Key 缺少 userbot:write 权限，请使用 bootstrap key 或给凭据加权限）'
      : ''
    ElMessage.error(`创建失败 [HTTP ${status ?? '-'}]：${msg}${hint}`)
  } finally {
    creating.value = false
  }
}

function openQr(bot: Bot) {
  qrBot.value = bot
  qrInfo.value = null
  qrDataUrl.value = null
  qrVisible.value = true
  pollQr()
}

async function pollQr() {
  stopQrPolling()
  if (!qrBot.value) return
  qrTimer.value = window.setInterval(async () => {
    try {
      const info = await getQr(qrBot.value!.botId)
      qrInfo.value = info
      qrDataUrl.value = await toQrDataUrl(info.qrImage)
      if (info.status === 'ONLINE') {
        stopQrPolling()
        ElMessage.success('扫码成功，Bot 已上线')
      }
    } catch {
      // 轮询失败忽略，下一轮重试
    }
  }, 2000)
}

function qrStatusHint(): string {
  const status = qrInfo.value?.status
  if (!status || status === 'STARTING') return '正在生成二维码，请稍候…'
  if (status === 'WAITING_QR') return '请使用微信扫码登录，连接后即可接收消息'
  if (status === 'ONLINE') return '扫码成功，Bot 已上线'
  if (status === 'RECONNECTING') return '连接异常，正在重连…'
  if (status === 'ERROR') return '登录失败，请关闭后重试或查看后端日志'
  return 'Bot 未在运行，请关闭后重新打开'
}

function stopQrPolling() {
  if (qrTimer.value !== null) {
    window.clearInterval(qrTimer.value)
    qrTimer.value = null
  }
}

function closeQr() {
  stopQrPolling()
  qrVisible.value = false
}

async function toQrDataUrl(content: string | null): Promise<string | null> {
  if (!content) return null
  if (content.startsWith('data:image')) return content
  try {
    return await QRCode.toDataURL(content, { width: 240, margin: 1 })
  } catch {
    return null
  }
}

async function removeBot(bot: Bot) {
  try {
    await ElMessageBox.confirm(`确认删除 Bot「${bot.displayName}」？删除后将从列表移除，需要重新创建并扫码连接。`, '删除 Bot', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteBot(bot.botId)
    ElMessage.success('Bot 已删除')
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

onMounted(() => { refresh() })
onBeforeUnmount(() => { stopQrPolling() })
</script>

<template>
  <div>
    <p class="page-kicker">BOT CONTROL</p>
    <h1 class="page-title">我的 Bot</h1>
    <p class="page-subtitle">创建你自己的微信 Bot，扫码连接后即可开始对话</p>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">+ 创建 Bot</el-button>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
      <span class="count-chip mono">{{ bots.length.toString().padStart(2, '0') }} BOTS</span>
    </div>

    <div class="panel table-panel">
      <el-table :data="bots" v-loading="loading" empty-text="还没有 Bot，点击「创建 Bot」开始">
        <el-table-column prop="displayName" label="名称" min-width="170">
          <template #default="{ row }">
            <div class="bot-name">
              <span class="bot-dot" :class="`tone-${statusMeta(row.runtimeStatus).tone}`"></span>
              <span>{{ row.displayName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="botId" label="Bot ID" min-width="140" class-name="mono" />
        <el-table-column label="运行状态" width="130">
          <template #default="{ row }">
            <span class="status-pill" :class="`tone-${statusMeta(row.runtimeStatus).tone}`">
              <span class="status-dot"></span>{{ statusMeta(row.runtimeStatus).label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最后活跃" width="180">
          <template #default="{ row }">
            <span class="muted mono small">{{ row.lastMessageAt || row.lastPollAt || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270">
          <template #default="{ row }">
            <el-button size="small" @click="openQr(row)">扫码连接</el-button>
            <el-button size="small" type="primary" @click="router.push(`/bots/${row.botId}`)">详情</el-button>
            <el-button size="small" type="danger" plain @click="removeBot(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="创建 Bot" width="440px">
      <el-input v-model="createName" placeholder="给 Bot 起个名字（可选）" maxlength="128" />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qrVisible" title="扫码连接 Bot" width="440px" :close-on-click-modal="false" @close="closeQr">
      <div v-if="qrBot" class="qr-body">
        <div class="qr-frame">
          <div class="qr-box">
            <img v-if="qrDataUrl" :src="qrDataUrl" alt="二维码" class="qr-img" />
            <div v-else class="qr-empty">{{ qrStatusHint() }}</div>
          </div>
          <span class="corner tl"></span><span class="corner tr"></span>
          <span class="corner bl"></span><span class="corner br"></span>
        </div>
        <div class="qr-status">
          <span class="status-pill" :class="`tone-${statusMeta(qrInfo?.status).tone}`">
            <span class="status-dot"></span>{{ statusMeta(qrInfo?.status).label }}
          </span>
          <span class="muted qr-hint">{{ qrStatusHint() }}</span>
        </div>
      </div>
      <template #footer>
        <el-button v-if="qrInfo?.status !== 'ONLINE'" @click="pollQr">重新获取</el-button>
        <el-button @click="closeQr">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.count-chip {
  margin-left: auto;
  font-size: 11px;
  letter-spacing: 1px;
  color: var(--accent-2);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 4px 10px;
}
.table-panel { padding: 8px 12px; }
.bot-name { display: flex; align-items: center; gap: 8px; font-weight: 700; }
.bot-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.small { font-size: 12px; }

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid transparent;
}
.status-dot { width: 6px; height: 6px; border-radius: 50%; }
.tone-ok { color: var(--accent-2); border-color: rgba(45, 225, 194, 0.35); background: rgba(45, 225, 194, 0.08); }
.tone-ok .status-dot, .tone-ok .bot-dot { background: var(--accent-2); box-shadow: 0 0 8px var(--accent-2); animation: pulse 1.8s ease-in-out infinite; }
.tone-warn { color: var(--accent); border-color: rgba(255, 180, 0, 0.35); background: rgba(255, 180, 0, 0.08); }
.tone-warn .status-dot, .tone-warn .bot-dot { background: var(--accent); box-shadow: 0 0 8px var(--accent); }
.tone-bad { color: var(--danger); border-color: rgba(255, 107, 107, 0.4); background: rgba(255, 107, 107, 0.08); }
.tone-bad .status-dot, .tone-bad .bot-dot { background: var(--danger); box-shadow: 0 0 8px var(--danger); }
.tone-info { color: var(--muted); border-color: var(--line); background: rgba(255, 255, 255, 0.03); }
.tone-info .status-dot, .tone-info .bot-dot { background: #8a94a6; }

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.82); }
}

.qr-body { display: flex; flex-direction: column; align-items: center; gap: 16px; }
.qr-frame {
  position: relative;
  width: 284px;
  height: 284px;
  border: 1px solid var(--line-strong);
  border-radius: 16px;
  padding: 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent), var(--panel-2);
}
.qr-box {
  width: 100%;
  height: 100%;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  overflow: hidden;
}
.qr-img { width: 250px; height: 250px; }
.qr-empty { color: var(--muted); font-size: 13px; padding: 0 18px; text-align: center; }
.corner { position: absolute; width: 18px; height: 18px; border-color: var(--accent); border-style: solid; }
.corner.tl { top: 4px; left: 4px; border-width: 2px 0 0 2px; border-radius: 6px 0 0 0; }
.corner.tr { top: 4px; right: 4px; border-width: 2px 2px 0 0; border-radius: 0 6px 0 0; }
.corner.bl { bottom: 4px; left: 4px; border-width: 0 0 2px 2px; border-radius: 0 0 0 6px; }
.corner.br { bottom: 4px; right: 4px; border-width: 0 2px 2px 0; border-radius: 0 0 6px 0; }
.qr-status { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.qr-hint { font-size: 12px; }
</style>
