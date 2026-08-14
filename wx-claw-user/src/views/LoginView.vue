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
    <div class="panel login-card">
      <h1 class="page-title">WX-Claw 用户控制台</h1>
      <p class="page-subtitle">创建并管理你自己的微信 Bot，扫码连接后查看聊天记录</p>
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
.login-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; }
.login-card { width: 420px; }
.login-btn { width: 100%; margin-top: 16px; }
.login-hint { font-size: 12px; margin-top: 14px; line-height: 1.6; }
.login-links { margin-top: 16px; display: flex; justify-content: center; gap: 8px; font-size: 13px; }
.login-links a { color: #2563eb; text-decoration: none; }
.login-links a:hover { text-decoration: underline; }
.divider { color: #c1c9d2; }
</style>
