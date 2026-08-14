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

async function deactivate(bot: Bot) {
  try {
    await ElMessageBox.confirm(`确认停用 Bot「${bot.displayName}」？停用后需要重新创建并扫码连接。`, '停用 Bot', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteBot(bot.botId)
    ElMessage.success('Bot 已停用')
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '停用失败')
  }
}

onMounted(() => { refresh() })
onBeforeUnmount(() => { stopQrPolling() })
</script>

<template>
  <div>
    <h1 class="page-title">我的 Bot</h1>
    <p class="page-subtitle">创建你自己的微信 Bot，扫码连接后即可开始对话</p>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">创建 Bot</el-button>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </div>

    <div class="panel">
      <el-table :data="bots" v-loading="loading" empty-text="还没有 Bot，点击「创建 Bot」开始">
        <el-table-column prop="displayName" label="名称" min-width="160" />
        <el-table-column prop="botId" label="Bot ID" min-width="140" class-name="mono" />
        <el-table-column label="运行状态" width="130">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG[row.runtimeStatus || 'UNKNOWN'] as any)">{{ row.runtimeStatus || 'UNKNOWN' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后活跃" width="170">
          <template #default="{ row }">
            <span class="muted">{{ row.lastMessageAt || row.lastPollAt || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" @click="openQr(row)">扫码连接</el-button>
            <el-button size="small" type="primary" @click="router.push(`/bots/${row.botId}`)">详情</el-button>
            <el-button size="small" type="danger" text @click="deactivate(row)">停用</el-button>
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

    <el-dialog v-model="qrVisible" title="扫码连接 Bot" width="420px" :close-on-click-modal="false" @close="closeQr">
      <div v-if="qrBot" class="qr-body">
        <div class="qr-box">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="二维码" class="qr-img" />
          <div v-else class="qr-empty">{{ qrStatusHint() }}</div>
        </div>
        <div class="qr-status">
          <el-tag :type="(STATUS_TAG[qrInfo?.status || 'UNKNOWN'] as any)">{{ qrInfo?.status || 'UNKNOWN' }}</el-tag>
          <span class="muted">{{ qrStatusHint() }}</span>
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
.qr-body { display: flex; flex-direction: column; align-items: center; gap: 14px; }
.qr-box { width: 260px; height: 260px; border: 1px solid #e7ecf2; border-radius: 12px; display: flex; align-items: center; justify-content: center; background: #fff; }
.qr-img { width: 240px; height: 240px; }
.qr-empty { color: #8492a6; font-size: 13px; }
.qr-status { display: flex; flex-direction: column; align-items: center; gap: 6px; }
</style>
