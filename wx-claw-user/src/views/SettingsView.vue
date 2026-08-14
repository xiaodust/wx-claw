<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAiConfig, getAiConfig, saveAiConfig } from '../api/user'
import type { AiConfig } from '../types/user'

const config = ref<AiConfig | null>(null)
const apiKey = ref('')
const loading = ref(false)
const saving = ref(false)

async function refresh() {
  loading.value = true
  try {
    config.value = await getAiConfig()
  } finally {
    loading.value = false
  }
}

async function save() {
  const key = apiKey.value.trim()
  if (!key) {
    ElMessage.warning('请输入 API Key')
    return
  }
  saving.value = true
  try {
    config.value = await saveAiConfig(key)
    apiKey.value = ''
    ElMessage.success('API Key 已保存并生效')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function clear() {
  try {
    await ElMessageBox.confirm('确认清除当前 API Key？清除后将回退到后端默认 Key。', '清除 API Key', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearAiConfig()
    await refresh()
    ElMessage.success('已清除，回退到后端默认 Key')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '清除失败')
  }
}

function formatTime(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => { refresh() })
</script>

<template>
  <div>
    <h1 class="page-title">API Key 设置</h1>
    <p class="page-subtitle">配置你自己的模型 API Key，Bot 对话将优先使用该 Key；不配置则使用后端默认 Key</p>

    <div class="panel">
      <el-descriptions :column="2" border style="margin-bottom: 20px;">
        <el-descriptions-item label="当前状态">
          <el-tag :type="config?.configured ? 'success' : 'info'">
            {{ config?.configured ? '已配置用户 Key' : '使用后端默认 Key' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Key（脱敏）">
          <span class="mono">{{ config?.apiKeyMasked || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="接入地址">{{ config?.baseUrl || '—' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(config?.updatedAt || null) }}</el-descriptions-item>
      </el-descriptions>

      <el-input
        v-model="apiKey"
        type="password"
        show-password
        placeholder="粘贴你的 API Key（如火山方舟 API Key）"
        style="margin-bottom: 14px;"
      />
      <div class="toolbar">
        <el-button type="primary" :loading="saving" @click="save">保存并生效</el-button>
        <el-button v-if="config?.configured" type="danger" plain @click="clear">清除（回退默认）</el-button>
      </div>
      <p class="muted" style="font-size: 12px; line-height: 1.6;">
        说明：保存后对当前租户下的所有 Bot 立即生效（后续对话请求使用你的 Key）；清除后自动回退到后端配置的默认 Key。
      </p>
    </div>
  </div>
</template>
