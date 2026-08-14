<script setup lang="ts">
import { ref } from 'vue'
import { forgotPassword } from '../api/public'

const input = ref('')
const loading = ref(false)
const submitted = ref(false)
const errorMsg = ref('')

async function submit() {
  const value = input.value.trim()
  if (!value) {
    errorMsg.value = '请输入注册时填写的用户名或邮箱'
    return
  }
  if (value.length > 128) {
    errorMsg.value = '输入内容过长'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await forgotPassword({ usernameOrEmail: value })
    submitted.value = true
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    if (status === 429) errorMsg.value = '请求过于频繁，请稍后再试'
    else errorMsg.value = message || '提交失败，请稍后再试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-wrap">
    <div class="bg-grid" aria-hidden="true"></div>
    <div class="panel card">
      <router-link class="brand" to="/">
        <span class="brand-mark">WX</span>
        <span class="brand-name">CLAW</span>
      </router-link>
      <p class="page-kicker">PASSWORD RECOVERY</p>
      <h1 class="page-title">找回密码</h1>
      <p class="page-subtitle">输入注册时的用户名或联系邮箱，重置链接会发送到对应邮箱</p>

      <template v-if="!submitted">
        <label class="field">
          <span class="field-label">用户名或邮箱</span>
          <el-input
            v-model="input"
            placeholder="例如 ops 或 ops@example.com"
            maxlength="128"
            @keyup.enter="submit"
          />
        </label>
        <p v-if="errorMsg" class="error-box" role="alert">{{ errorMsg }}</p>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">发送重置链接</el-button>
      </template>

      <div v-else class="ok-box">
        <b>提交成功</b>
        <p>如果账号存在，重置链接已发送到对应邮箱，链接 30 分钟内有效。请查收邮件（注意垃圾箱）。</p>
        <router-link class="back-login" to="/login">返回登录</router-link>
      </div>

      <p class="foot-note">
        <router-link to="/login">← 返回登录</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.page-wrap {
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
.card {
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
.card .page-title { font-size: 24px; }
.card .page-subtitle { margin-bottom: 20px; }
.field { display: block; margin-bottom: 14px; }
.field-label { display: block; font-size: 12px; font-weight: 700; margin-bottom: 7px; color: var(--muted); }
.error-box {
  margin: 0 0 12px;
  padding: 9px 12px;
  border: 1px solid rgba(255, 107, 107, 0.4);
  border-radius: 8px;
  background: rgba(255, 107, 107, 0.08);
  color: var(--danger);
  font-size: 13px;
}
.submit-btn { width: 100%; margin-top: 6px; font-weight: 800; }
.ok-box {
  border: 1px solid rgba(45, 225, 194, 0.35);
  border-radius: 10px;
  background: rgba(45, 225, 194, 0.07);
  color: #9fe8db;
  padding: 14px;
  font-size: 13px;
  line-height: 1.7;
}
.ok-box p { margin: 6px 0 12px; }
.back-login { color: var(--accent); text-decoration: none; font-weight: 700; }
.foot-note { margin: 18px 0 0; text-align: center; font-size: 13px; }
.foot-note a { color: var(--accent); text-decoration: none; }
</style>
