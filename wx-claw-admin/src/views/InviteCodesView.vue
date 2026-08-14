<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { disableInviteCode, generateInviteCodes, getInviteCodes } from '../api/admin'
import type { GenerateInviteCodesRequest, InviteCode } from '../types/admin'

const rows = ref<InviteCode[]>([])
const loading = ref(false)

const genVisible = ref(false)
const generating = ref(false)
const genForm = reactive({ count: 5, quota: '' as string, days: '' as string, remark: '' })

const resultVisible = ref(false)
const generatedCodes = ref<string[]>([])
const copied = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await getInviteCodes()
  } finally {
    loading.value = false
  }
}

function openGen() {
  genForm.count = 5
  genForm.quota = ''
  genForm.days = ''
  genForm.remark = ''
  genVisible.value = true
}

async function submitGen() {
  if (genForm.count < 1 || genForm.count > 50) {
    ElMessage.warning('单次生成 1-50 个')
    return
  }
  generating.value = true
  try {
    const quota = genForm.quota.trim() ? Number(genForm.quota) : null
    const days = genForm.days.trim() ? Number(genForm.days) : null
    const payload: GenerateInviteCodesRequest = {
      count: genForm.count,
      quota: quota && quota > 0 ? quota : null,
      expiresAt: days && days > 0 ? new Date(Date.now() + days * 86400000).toISOString() : null,
      remark: genForm.remark.trim() || undefined,
    }
    const result = await generateInviteCodes(payload)
    genVisible.value = false
    generatedCodes.value = result.codes
    resultVisible.value = true
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

async function copyAll() {
  try {
    await navigator.clipboard.writeText(generatedCodes.value.join('\n'))
    copied.value = true
    window.setTimeout(() => { copied.value = false }, 2000)
  } catch {
    ElMessage.warning('复制失败，请手动选择')
  }
}

async function disable(row: InviteCode) {
  try {
    await ElMessageBox.confirm(`确认停用邀请码「${row.code}」？停用后无法再用于注册。`, '停用邀请码', { type: 'warning' })
  } catch {
    return
  }
  try {
    await disableInviteCode(row.code)
    ElMessage.success('已停用')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '停用失败')
  }
}

function fmt(v?: string | null): string {
  return v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function quotaText(row: InviteCode): string {
  return row.quota == null ? `${row.usedCount} / 不限` : `${row.usedCount} / ${row.quota}`
}

onMounted(load)
</script>

<template>
  <div>
    <h1 class="page-title">注册邀请码</h1>
    <p class="page-subtitle">注册需要邀请码（平台级配置）；生成后邀请码只展示一次，请及时分发</p>

    <section class="panel">
      <div class="toolbar">
        <el-button type="primary" @click="openGen">+ 生成邀请码</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <span class="muted mono">共 {{ rows.length }} 个</span>
      </div>
      <el-table :data="rows" v-loading="loading" empty-text="还没有邀请码，点击「生成邀请码」创建">
        <el-table-column label="邀请码" min-width="150">
          <template #default="{ row }">
            <span class="mono code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="light">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已用 / 配额" width="120">
          <template #default="{ row }">{{ quotaText(row) }}</template>
        </el-table-column>
        <el-table-column label="过期时间" width="180">
          <template #default="{ row }">{{ fmt(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="创建人" width="140">
          <template #default="{ row }">{{ row.createdBy || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="disable(row)">停用</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="genVisible" title="生成邀请码" width="460px">
      <el-form label-width="90px">
        <el-form-item label="数量">
          <el-input-number v-model="genForm.count" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="配额">
          <el-input v-model="genForm.quota" placeholder="留空 = 不限次数" style="width: 220px" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-input v-model="genForm.days" placeholder="留空 = 永不过期（天数）" style="width: 220px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="genForm.remark" placeholder="例如：内部测试" maxlength="200" style="width: 280px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="submitGen">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="邀请码已生成（仅展示一次）" width="480px">
      <div class="code-list">
        <code v-for="c in generatedCodes" :key="c" class="code-item">{{ c }}</code>
      </div>
      <template #footer>
        <el-button @click="copyAll">{{ copied ? '已复制 ✓' : '全部复制' }}</el-button>
        <el-button type="primary" @click="resultVisible = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; }
.code { font-weight: 600; letter-spacing: 1px; }
.code-list { display: flex; flex-direction: column; gap: 8px; max-height: 320px; overflow: auto; }
.code-item {
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e7ecf2;
  border-radius: 8px;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 14px;
  letter-spacing: 1px;
  user-select: all;
}
</style>
