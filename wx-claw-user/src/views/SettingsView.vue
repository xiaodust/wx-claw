<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAiConfig, clearModel, getAiConfigs, getModelCatalog, saveAiConfig, saveModel } from '../api/user'
import type { AiConfigEntry, AiConfigs, ModelCatalog, ModelOption } from '../types/user'

const configs = ref<AiConfigs | null>(null)
const catalog = ref<ModelCatalog | null>(null)
const inputs = reactive<Record<string, string>>({})
const modelInputs = reactive<Record<string, string>>({})
const customModelMode = reactive<Record<string, boolean>>({})
const capProviders = reactive<Record<string, string>>({ chat: 'ark', image: 'siliconflow', video: 'ark' })
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
  { key: 'image', title: '图片生成', desc: 'SiliconFlow / 火山方舟 / OpenAI；Kolors 免费，其余模型按量计费', modelLabel: '生成模型', modelCatalog: 'image' },
  { key: 'video', title: '视频生成', desc: '火山方舟 Seedance / OpenAI Sora / 阿里云通义万相；不填 Key 时：ark 服务商复用对话 Key，否则用后端默认', modelLabel: '视频模型', modelCatalog: 'video' },
  { key: 'tts', title: '语音合成', desc: '火山引擎豆包语音（openspeech.bytedance.com）；Key 在豆包语音控制台创建，需开通 seed-audio-1.0 语音合成/音频生成服务，欠费或未开通会报 403' },
  { key: 'search', title: '联网搜索', desc: '博查 Bocha（无模型概念）' },
]

const chatModels = computed<ModelOption[]>(() =>
  catalog.value?.chatProviders.find(p => p.id === provider.value)?.models || [])

function entry(key: string): AiConfigEntry | null {
  return configs.value?.[key as keyof AiConfigs] ?? null
}

function modelOptions(key: string): ModelOption[] {
  const cap = capabilities.find(c => c.key === key)
  if (!cap?.modelCatalog || !catalog.value) return []
  if (cap.modelCatalog === 'image') {
    return catalog.value.imageProviders.find(p => p.id === capProviders[key])?.models || []
  }
  if (cap.modelCatalog === 'video') {
    return catalog.value.videoProviders.find(p => p.id === capProviders[key])?.models || []
  }
  return chatModels.value
}

function providerOptions(key: string) {
  const cap = capabilities.find(c => c.key === key)
  if (!cap?.modelCatalog || !catalog.value) return []
  if (cap.modelCatalog === 'image') return catalog.value.imageProviders
  if (cap.modelCatalog === 'video') return catalog.value.videoProviders
  return catalog.value.chatProviders
}

function onProviderChange(key: string) {
  if (key === 'chat') {
    provider.value = capProviders.chat
  }
  modelInputs[key] = ''
}

function toggleCustomModel(key: string) {
  customModelMode[key] = !customModelMode[key]
  if (customModelMode[key] && !(modelInputs[key] || '').trim()) {
    modelInputs[key] = entry(key)?.model || ''
  }
}

async function refresh() {
  loading.value = true
  try {
    configs.value = await getAiConfigs()
    catalog.value = await getModelCatalog()
    capProviders.chat = entry('chat')?.provider || 'ark'
    provider.value = capProviders.chat
    capProviders.image = entry('image')?.provider || 'siliconflow'
    capProviders.video = entry('video')?.provider || 'ark'
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
  payload.provider = key === 'chat' ? provider.value : capProviders[key]
  if (key === 'chat' && provider.value === 'custom') {
    if (!customBaseUrl.value.trim()) {
      ElMessage.warning('自定义服务商需要填写 baseUrl')
      return
    }
    payload.baseUrl = customBaseUrl.value.trim()
  }
  const mismatch = findModelInOtherProvider(key, model)
  if (mismatch) {
    try {
      await ElMessageBox.confirm(
        `模型「${model}」属于服务商「${mismatch.name}」，当前选择的是「${currentProviderName(key)}」。确认仍以当前服务商保存？`,
        '模型与服务商不匹配',
        { type: 'warning' },
      )
    } catch {
      return
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

function currentProviderName(key: string): string {
  const id = key === 'chat' ? provider.value : capProviders[key]
  return providerOptions(key).find(p => p.id === id)?.name || id || '后端默认'
}

function findModelInOtherProvider(key: string, model: string) {
  const cap = capabilities.find(c => c.key === key)
  if (!cap?.modelCatalog || !catalog.value) return null
  const currentId = key === 'chat' ? provider.value : capProviders[key]
  const providers = cap.modelCatalog === 'image'
    ? catalog.value.imageProviders
    : cap.modelCatalog === 'video'
      ? catalog.value.videoProviders
      : catalog.value.chatProviders
  if (currentId === 'custom') return null
  return providers.find(p => p.id !== currentId && p.models.some(m => m.name === model)) || null
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
    <p class="page-kicker">KEY &amp; MODEL CONFIG</p>
    <h1 class="page-title">API Key 与模型设置</h1>
    <p class="page-subtitle">
      按能力配置你自己的 API Key 与模型；模型列表与服务商对应（选择服务商后，只显示该服务商的模型）。
      模型支持下拉选择，也可以切换到"自定义"直接输入目录外的模型名。
      未配置的能力自动回退到后端默认。
    </p>

    <div v-loading="loading" class="cap-list">
      <div v-for="cap in capabilities" :key="cap.key" class="panel cap-card">
        <span class="cap-index mono">{{ cap.key.toUpperCase() }}</span>
        <div class="cap-head">
          <div>
            <div class="cap-title">{{ cap.title }}</div>
            <div class="muted cap-desc">{{ cap.desc }}</div>
          </div>
          <span class="config-chip" :class="entry(cap.key)?.configured ? 'on' : 'off'">
            <span class="chip-dot"></span>{{ entry(cap.key)?.configured ? '已配置用户 Key' : '使用后端默认' }}
          </span>
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
          <div class="cap-row model-row">
            <span class="muted">服务商：</span>
            <el-select v-model="capProviders[cap.key]" style="width: 240px;" @change="onProviderChange(cap.key)">
              <el-option v-for="p in providerOptions(cap.key)" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <template v-if="cap.key === 'chat' && capProviders[cap.key] === 'custom'">
              <span class="muted">baseUrl：</span>
              <el-input v-model="customBaseUrl" placeholder="https://your-endpoint/v1" style="width: 320px;" />
            </template>
            <span v-else class="muted mono">{{ providerOptions(cap.key).find(p => p.id === capProviders[cap.key])?.baseUrl }}</span>
          </div>
          <div class="cap-row model-row">
            <span class="muted">{{ cap.modelLabel }}：</span>
            <el-select v-if="!customModelMode[cap.key]" v-model="modelInputs[cap.key]" filterable allow-create default-first-option placeholder="选择模型" style="width: 320px;">
              <el-option v-for="m in modelOptions(cap.key)" :key="m.name" :label="m.name" :value="m.name">
                <span class="option-name">{{ m.name }}</span>
                <el-tag v-if="m.free" type="success" size="small" effect="light" class="free-tag">免费</el-tag>
              </el-option>
            </el-select>
            <el-input v-else v-model="modelInputs[cap.key]" placeholder="输入自定义模型名，回车保存" clearable style="width: 320px;" @keyup.enter="saveModelCap(cap.key)" />
            <el-button text type="primary" @click="toggleCustomModel(cap.key)">
              {{ customModelMode[cap.key] ? '从列表选择' : '自定义' }}
            </el-button>
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
.cap-card { display: flex; flex-direction: column; gap: 12px; position: relative; }
.cap-card::before {
  content: "";
  position: absolute;
  top: 0; left: 20px; right: 20px;
  height: 2px;
  background: linear-gradient(90deg, var(--accent), transparent 75%);
  border-radius: 2px;
}
.cap-index {
  font-size: 10px;
  letter-spacing: 3px;
  color: var(--accent-2);
  margin-bottom: -4px;
  font-weight: 700;
}
.cap-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.cap-title { font-weight: 700; font-size: 15px; }
.cap-desc { font-size: 12px; margin-top: 2px; }
.cap-masked { font-size: 13px; }
.cap-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.model-row { padding-top: 10px; border-top: 1px dashed var(--line); }
.cap-actions { display: flex; gap: 10px; align-items: center; flex: 1; }
.cap-actions .el-input { flex: 1; min-width: 220px; max-width: 420px; }
.free-tag { margin-left: 8px; flex-shrink: 0; }
.config-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 3px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid transparent;
  flex-shrink: 0;
}
.config-chip .chip-dot { width: 6px; height: 6px; border-radius: 50%; }
.config-chip.on { color: var(--accent-2); border-color: rgba(45, 225, 194, 0.35); background: rgba(45, 225, 194, 0.08); }
.config-chip.on .chip-dot { background: var(--accent-2); box-shadow: 0 0 8px var(--accent-2); }
.config-chip.off { color: var(--muted); border-color: var(--line); background: rgba(255, 255, 255, 0.03); }
.config-chip.off .chip-dot { background: #8a94a6; }
.option-name { font-family: "JetBrains Mono", Consolas, monospace; }
</style>
