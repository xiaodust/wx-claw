<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAiConfig, getAiConfigs, saveAiConfig } from '../api/user'
import type { AiConfigEntry, AiConfigs } from '../types/user'

const configs = ref<AiConfigs | null>(null)
const inputs = reactive<Record<string, string>>({})
const loading = ref(false)
const saving = ref<string | null>(null)

const capabilities = [
  { key: 'chat', title: '文本对话/理解', desc: '火山方舟 OpenAI 兼容：对话、图片理解、向量记忆默认' },
  { key: 'image', title: '图片生成', desc: 'SiliconFlow（Kolors）' },
  { key: 'video', title: '视频生成', desc: '火山方舟 Seedance' },
  { key: 'videoDashscope', title: '视频生成（阿里云）', desc: '通义万相 DashScope' },
  { key: 'tts', title: '语音合成', desc: '火山引擎 TTS' },
  { key: 'search', title: '联网搜索', desc: '博查 Bocha' },
]

function entry(key: string): AiConfigEntry | null {
  return configs.value?.[key as keyof AiConfigs] ?? null
}

async function refresh() {
  loading.value = true
  try {
    configs.value = await getAiConfigs()
  } finally {
    loading.value = false
  }
}

async function save(key: string) {
  const apiKey = (inputs[key] || '').trim()
  if (!apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }
  saving.value = key
  try {
    await saveAiConfig(key, apiKey)
    inputs[key] = ''
    await refresh()
    ElMessage.success('已保存并生效')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = null
  }
}

async function clear(key: string) {
  try {
    await ElMessageBox.confirm('确认清除该能力的 API Key？清除后将回退到后端默认配置。', '清除 API Key', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearAiConfig(key)
    await refresh()
    ElMessage.success('已清除，回退到后端默认 Key')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '清除失败')
  }
}

onMounted(() => { refresh() })
</script>

<template>
  <div>
    <h1 class="page-title">API Key 设置</h1>
    <p class="page-subtitle">
      按能力配置你自己的模型 API Key，Bot 使用时会优先采用你的 Key；未配置的能力自动回退到后端默认 Key。
    </p>

    <div v-loading="loading" class="cap-list">
      <div v-for="cap in capabilities" :key="cap.key" class="panel cap-card">
        <div class="cap-head">
          <div>
            <div class="cap-title">{{ cap.title }}</div>
            <div class="muted cap-desc">{{ cap.desc }}</div>
          </div>
          <el-tag :type="entry(cap.key)?.configured ? 'success' : 'info'">
            {{ entry(cap.key)?.configured ? '已配置用户 Key' : '使用后端默认' }}
          </el-tag>
        </div>
        <div class="cap-masked">
          <span class="muted">当前 Key：</span>
          <span class="mono">{{ entry(cap.key)?.apiKeyMasked || '—' }}</span>
        </div>
        <div class="cap-actions">
          <el-input
            v-model="inputs[cap.key]"
            type="password"
            show-password
            placeholder="粘贴该能力的 API Key"
          />
          <el-button type="primary" :loading="saving === cap.key" @click="save(cap.key)">保存并生效</el-button>
          <el-button v-if="entry(cap.key)?.configured" type="danger" plain @click="clear(cap.key)">清除</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cap-list { display: flex; flex-direction: column; gap: 14px; }
.cap-card { display: flex; flex-direction: column; gap: 12px; }
.cap-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.cap-title { font-weight: 700; font-size: 15px; }
.cap-desc { font-size: 12px; margin-top: 2px; }
.cap-masked { font-size: 13px; }
.cap-actions { display: flex; gap: 10px; align-items: center; }
.cap-actions .el-input { max-width: 420px; }
</style>
