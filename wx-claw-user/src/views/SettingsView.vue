<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAiConfig, clearModel, getAiConfigs, getModelCatalog, saveAiConfig, saveModel } from '../api/user'
import type { AiConfigEntry, AiConfigs, ModelCatalog } from '../types/user'

const configs = ref<AiConfigs | null>(null)
const catalog = ref<ModelCatalog | null>(null)
const inputs = reactive<Record<string, string>>({})
const modelInputs = reactive<Record<string, string>>({})
const provider = ref('ark')
const customBaseUrl = ref('')
const loading = ref(false)
const savingKey = ref<string | null>(null)
const savingModel = ref<string | null>(null)

interface CapabilityDef {
  key: string
  title: string
  desc: string
  modelLabel?: string
  modelCatalog?: string
  keyHidden?: boolean
}

const capabilities: CapabilityDef[] = [
  { key: 'chat', title: '对话 API Key（多服务商）', desc: '文本对话与图片理解；选择服务商后模型列表与接入地址随之切换', modelLabel: '对话模型', modelCatalog: 'chat' },
  { key: 'image', title: '图片生成', desc: 'SiliconFlow（Kolors）', modelLabel: '生成模型', modelCatalog: 'image' },
  { key: 'video', title: '视频生成（Seedance）', desc: '火山方舟视频模型；不填 Key 时：对话为火山方舟则复用对话 Key，否则用后端默认', modelLabel: '视频模型', modelCatalog: 'video' },
  { key: 'videoDashscope', title: '视频生成（阿里云）', desc: '通义万相 DashScope（模型使用后端默认）' },
  { key: 'tts', title: '语音合成', desc: '火山引擎 TTS（模型使用后端默认）' },
  { key: 'search', title: '联网搜索', desc: '博查 Bocha（无模型概念）' },
]

const chatModels = computed(() =>
  catalog.value?.chatProviders.find(p => p.id === provider.value)?.models || [])
const selectedProvider = computed(() =>
  catalog.value?.chatProviders.find(p => p.id === provider.value))

function entry(key: string): AiConfigEntry | null {
  return configs.value?.[key as keyof AiConfigs] ?? null
}

function modelOptions(key: string): string[] {
  const cap = capabilities.find(c => c.key === key)
  if (!cap?.modelCatalog || !catalog.value) return []
  if (cap.modelCatalog === 'image') return catalog.value.imageModels
  if (cap.modelCatalog === 'video') return catalog.value.videoModels
  return chatModels.value
}

async function refresh() {
  loading.value = true
  try {
    configs.value = await getAiConfigs()
    catalog.value = await getModelCatalog()
  } finally {
    loading.value = false
  }
}

async function saveKey(key: string) {
  const apiKey = (inputs[key] || '').trim()
  if (!apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }
  savingKey.value = key
  try {
    await saveAiConfig(key, apiKey)
    inputs[key] = ''
    await refresh()
    ElMessage.success('已保存并生效')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingKey.value = null
  }
}

async function clearKey(key: string) {
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

async function saveModelCap(key: string) {
  const model = (modelInputs[key] || '').trim()
  if (!model) {
    ElMessage.warning('请选择模型')
    return
  }
  const payload: { model: string; provider?: string; baseUrl?: string } = { model }
  if (key === 'chat') {
    payload.provider = provider.value
    if (provider.value === 'custom') {
      if (!customBaseUrl.value.trim()) {
        ElMessage.warning('自定义服务商需要填写 baseUrl')
        return
      }
      payload.baseUrl = customBaseUrl.value.trim()
    }
  }
  savingModel.value = key
  try {
    await saveModel(key, payload)
    modelInputs[key] = ''
    await refresh()
    ElMessage.success('模型已保存并生效')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存模型失败')
  } finally {
    savingModel.value = null
  }
}

async function clearModelCap(key: string) {
  try {
    await ElMessageBox.confirm('确认恢复该能力的后端默认模型？', '恢复默认模型', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearModel(key)
    await refresh()
    ElMessage.success('已恢复后端默认模型')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '恢复失败')
  }
}

onMounted(() => { refresh() })
</script>

<template>
  <div>
    <h1 class="page-title">API Key 与模型设置</h1>
    <p class="page-subtitle">
      按能力配置你自己的 API Key 与模型；模型列表与服务商对应（聊天能力选择服务商后，只显示该服务商的模型）。
      未配置的能力自动回退到后端默认。
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

        <div v-if="!cap.keyHidden" class="cap-row">
          <div class="cap-masked">
            <span class="muted">当前 Key：</span>
            <span class="mono">{{ entry(cap.key)?.apiKeyMasked || '—' }}</span>
          </div>
          <div class="cap-actions">
            <el-input v-model="inputs[cap.key]" type="password" show-password placeholder="粘贴该能力的 API Key" />
            <el-button type="primary" :loading="savingKey === cap.key" @click="saveKey(cap.key)">保存并生效</el-button>
            <el-button v-if="entry(cap.key)?.configured" type="danger" plain @click="clearKey(cap.key)">清除</el-button>
          </div>
        </div>

        <template v-if="cap.modelCatalog">
          <div v-if="cap.key === 'chat'" class="cap-row model-row">
            <span class="muted">服务商：</span>
            <el-select v-model="provider" style="width: 220px;">
              <el-option v-for="p in catalog?.chatProviders || []" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <template v-if="provider === 'custom'">
              <span class="muted">baseUrl：</span>
              <el-input v-model="customBaseUrl" placeholder="https://your-endpoint/v1" style="width: 320px;" />
            </template>
            <span v-else class="muted mono">{{ selectedProvider?.baseUrl }}</span>
          </div>
          <div class="cap-row model-row">
            <span class="muted">{{ cap.modelLabel }}：</span>
            <el-select v-model="modelInputs[cap.key]" filterable allow-create default-first-option placeholder="选择或输入模型" style="width: 320px;">
              <el-option v-for="m in modelOptions(cap.key)" :key="m" :label="m" :value="m" />
            </el-select>
            <el-button type="primary" plain :loading="savingModel === cap.key" @click="saveModelCap(cap.key)">保存模型</el-button>
            <el-button v-if="entry(cap.key)?.model" type="danger" text @click="clearModelCap(cap.key)">恢复默认</el-button>
            <span class="muted">当前：<span class="mono">{{ entry(cap.key)?.model || '后端默认' }}</span></span>
          </div>
        </template>
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
.cap-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.model-row { padding-top: 10px; border-top: 1px dashed #e7ecf2; }
.cap-actions { display: flex; gap: 10px; align-items: center; flex: 1; }
.cap-actions .el-input { flex: 1; min-width: 220px; max-width: 420px; }
</style>
