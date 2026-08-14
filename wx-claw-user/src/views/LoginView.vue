<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const apiKey = ref('')
const loading = ref(false)

function login() {
  const key = apiKey.value.trim()
  if (!key) {
    ElMessage.warning('请输入 API Key')
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
      <p class="page-subtitle">输入租户 API Key，管理 Bot、会话与 AI 能力配置</p>
      <el-input
        v-model="apiKey"
        type="password"
        show-password
        placeholder="请输入 API Key（格式：credentialId.secret）"
        @keyup.enter="login"
      />
      <el-button type="primary" class="login-btn" :loading="loading" @click="login">登录</el-button>
      <p class="muted login-hint">
        首次使用可用后端配置的 bootstrap key；该 Key 需具备 userbot:* 、conversation:read、aiconfig:* 权限。
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
  width: 420px;
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
.login-card .page-subtitle { margin-bottom: 20px; }
.login-btn { width: 100%; margin-top: 16px; font-weight: 800; }
.login-hint { font-size: 12px; margin-top: 14px; line-height: 1.6; }
.login-links { margin-top: 16px; display: flex; justify-content: center; gap: 8px; font-size: 13px; }
.login-links a { color: var(--accent); text-decoration: none; }
.login-links a:hover { text-decoration: underline; }
.divider { color: var(--muted); }
</style>
