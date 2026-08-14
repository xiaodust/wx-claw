<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginTenant } from '../api/public'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref<'account' | 'apikey'>('account')
const account = reactive({ username: '', password: '' })
const apiKey = ref('')
const loading = ref(false)
const errorMsg = ref('')

function switchTab() {
  errorMsg.value = ''
}

async function login() {
  if (loading.value) return
  errorMsg.value = ''
  if (activeTab.value === 'account') {
    const username = account.username.trim()
    if (!username || !account.password) {
      errorMsg.value = '请输入用户名和密码'
      return
    }
    loading.value = true
    try {
      const result = await loginTenant({ username, password: account.password })
      authStore.login(result.sessionToken)
      router.push('/bots')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      if (status === 401) errorMsg.value = '用户名或密码错误'
      else if (status === 429) errorMsg.value = '尝试过于频繁，请稍后再试'
      else errorMsg.value = message || '登录失败，请稍后再试'
    } finally {
      loading.value = false
    }
    return
  }

  const key = apiKey.value.trim()
  if (!key) {
    errorMsg.value = '请输入 API Key'
    return
  }
  loading.value = true
  authStore.login(key)
  router.push('/bots')
  loading.value = false
}
</script>

<template>
  <div class="login-wrap">
    <div class="bg-grid" aria-hidden="true"></div>
    <div class="panel login-card">
      <router-link class="brand" to="/">
        <span class="brand-mark">WX</span>
        <span class="brand-name">CLAW</span>
      </router-link>
      <p class="page-kicker">CONSOLE SIGN IN</p>
      <h1 class="page-title">登录控制台</h1>
      <p class="page-subtitle">管理 Bot、会话与 AI 能力配置</p>

      <el-tabs v-model="activeTab" class="login-tabs" @tab-change="switchTab">
        <el-tab-pane label="账号密码" name="account">
          <label class="field">
            <span class="field-label">用户名</span>
            <el-input
              v-model="account.username"
              autocomplete="username"
              placeholder="注册时设置的用户名"
              @keyup.enter="login"
            />
          </label>
          <label class="field">
            <span class="field-label">密码</span>
            <el-input
              v-model="account.password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="登录密码"
              @keyup.enter="login"
            />
          </label>
        </el-tab-pane>
        <el-tab-pane label="API Key" name="apikey">
          <label class="field">
            <span class="field-label">API Key</span>
            <el-input
              v-model="apiKey"
              type="password"
              show-password
              placeholder="credentialId.secret（接口/旧账号使用）"
              @keyup.enter="login"
            />
          </label>
        </el-tab-pane>
      </el-tabs>

      <p v-if="errorMsg" class="login-error" role="alert">{{ errorMsg }}</p>
      <el-button type="primary" class="login-btn" :loading="loading" @click="login">
        {{ activeTab === 'account' ? '登录' : '使用 Key 登录' }}
      </el-button>
      <p class="muted login-hint">
        新注册租户使用账号密码登录；API Key 仅用于接口调用，也可作为旧账号的登录兜底。
      </p>
      <p class="login-links">
        <router-link to="/">返回首页</router-link>
        <span class="divider">·</span>
        <router-link to="/register">还没有账号？免费注册</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  position: relative;
  overflow: hidden;
}
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(ellipse 70% 55% at 50% 0%, #000 30%, transparent 75%);
  pointer-events: none;
}
.login-card {
  width: 430px;
  position: relative;
  padding: 34px 32px;
  border-radius: 16px;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  text-decoration: none;
  color: var(--fg);
  margin-bottom: 22px;
}
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: var(--accent);
  color: #14161a;
  font-weight: 800;
  font-size: 12px;
  border-radius: 8px;
}
.brand-name { font-weight: 800; letter-spacing: 2px; font-size: 14px; }
.login-card .page-title { font-size: 24px; }
.login-card .page-subtitle { margin-bottom: 14px; }
.login-tabs :deep(.el-tabs__item) { font-weight: 600; }
.field { display: block; margin-bottom: 14px; }
.field-label { display: block; font-size: 12px; font-weight: 700; margin-bottom: 7px; color: var(--muted); }
.login-error {
  margin: 12px 0 0;
  padding: 9px 12px;
  border: 1px solid rgba(255, 107, 107, 0.4);
  border-radius: 8px;
  background: rgba(255, 107, 107, 0.08);
  color: var(--danger);
  font-size: 13px;
}
.login-btn { width: 100%; margin-top: 16px; font-weight: 800; }
.login-hint { font-size: 12px; margin-top: 14px; line-height: 1.6; }
.login-links { margin-top: 16px; display: flex; justify-content: center; gap: 8px; font-size: 13px; }
.login-links a { color: var(--accent); text-decoration: none; }
.login-links a:hover { text-decoration: underline; }
.divider { color: var(--muted); }
</style>
