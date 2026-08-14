<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { activateAccount, probeAccount, sendEmailCode } from '../api/public'
import { useAuthStore } from '../stores/auth'
import type { AccountInfo } from '../types/user'

const router = useRouter()
const authStore = useAuthStore()

const apiKey = ref('')
const probing = ref(false)
const probeError = ref('')
const accountInfo = ref<AccountInfo | null>(null)

const setupForm = reactive({ username: '', contactEmail: '', emailCode: '', password: '', confirmPassword: '' })
const setupError = ref('')
const settingUp = ref(false)
const codeSending = ref(false)
const codeCountdown = ref(0)
let codeTimer: number | undefined

const emailPattern = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const usernamePattern = /^[a-z0-9_-]{3,32}$/
const showSetup = computed(() => accountInfo.value !== null && !accountInfo.value.hasAccount)

async function probe() {
  const key = apiKey.value.trim()
  if (!key) {
    probeError.value = '请输入 API Key'
    return
  }
  probing.value = true
  probeError.value = ''
  accountInfo.value = null
  try {
    accountInfo.value = await probeAccount(key)
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    probeError.value = status === 401 ? 'API Key 无效，请检查后重试' : '验证失败，请稍后再试'
  } finally {
    probing.value = false
  }
}

async function sendCode() {
  if (codeSending.value || codeCountdown.value > 0) return
  if (!emailPattern.test(setupForm.contactEmail.trim())) {
    setupError.value = '邮箱格式不正确'
    return
  }
  setupError.value = ''
  codeSending.value = true
  try {
    await sendEmailCode({ email: setupForm.contactEmail.trim().toLowerCase(), purpose: 'SETUP' })
    codeCountdown.value = 60
    codeTimer = window.setInterval(() => {
      codeCountdown.value -= 1
      if (codeCountdown.value <= 0 && codeTimer) {
        window.clearInterval(codeTimer)
        codeTimer = undefined
      }
    }, 1000)
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    setupError.value = status === 429 ? '发送太频繁，请稍后再试' : '验证码发送失败，请稍后再试'
  } finally {
    codeSending.value = false
  }
}

async function submit() {
  if (settingUp.value) return
  setupError.value = ''
  if (!usernamePattern.test(setupForm.username.trim().toLowerCase())) {
    setupError.value = '用户名需为 3-32 位，仅支持小写字母、数字、下划线、连字符'
    return
  }
  if (!emailPattern.test(setupForm.contactEmail.trim())) {
    setupError.value = '邮箱格式不正确'
    return
  }
  if (setupForm.emailCode.trim().length < 4) {
    setupError.value = '请输入邮箱验证码'
    return
  }
  if (setupForm.password.length < 8 || setupForm.password.length > 128) {
    setupError.value = '密码长度需为 8-128 位'
    return
  }
  if (setupForm.password !== setupForm.confirmPassword) {
    setupError.value = '两次输入的密码不一致'
    return
  }
  settingUp.value = true
  try {
    const result = await activateAccount(apiKey.value.trim(), {
      username: setupForm.username.trim().toLowerCase(),
      contactEmail: setupForm.contactEmail.trim().toLowerCase(),
      emailCode: setupForm.emailCode.trim(),
      password: setupForm.password,
    })
    authStore.login(result.sessionToken)
    router.push('/bots')
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    if (status === 409) setupError.value = message || '用户名已被注册'
    else setupError.value = message || '激活失败，请稍后再试'
  } finally {
    settingUp.value = false
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
      <p class="page-kicker">ACCOUNT ACTIVATION</p>
      <h1 class="page-title">激活账号</h1>
      <p class="page-subtitle">仅用于没有登录账号的租户：用 API Key 完成一次身份校验后，设置用户名、邮箱和密码。</p>

      <template v-if="!showSetup">
        <label class="field">
          <span class="field-label">API Key</span>
          <el-input
            v-model="apiKey"
            type="password"
            show-password
            placeholder="credentialId.secret（仅用于本次校验，不会保存）"
            @keyup.enter="probe"
          />
        </label>
        <p v-if="probeError" class="error-box" role="alert">{{ probeError }}</p>
        <el-button type="primary" class="submit-btn" :loading="probing" @click="probe">验证并继续</el-button>

        <div v-if="accountInfo && accountInfo.hasAccount" class="ok-box">
          <b>该租户已有账号</b>
          <p>直接用用户名和密码登录即可。</p>
          <router-link class="back-login" to="/login">去登录 →</router-link>
        </div>
      </template>

      <template v-else>
        <label class="field">
          <span class="field-label">登录用户名</span>
          <el-input v-model="setupForm.username" maxlength="32" placeholder="小写字母/数字/_/-，3-32 位" />
        </label>
        <label class="field">
          <span class="field-label">联系邮箱</span>
          <div class="email-row">
            <el-input v-model="setupForm.contactEmail" maxlength="128" placeholder="用于验证与密码找回" @input="setupForm.emailCode = ''" />
            <el-button :disabled="codeSending || codeCountdown > 0" @click="sendCode">
              {{ codeCountdown > 0 ? `${codeCountdown}s` : (codeSending ? '发送中…' : '发送验证码') }}
            </el-button>
          </div>
        </label>
        <label class="field">
          <span class="field-label">邮箱验证码</span>
          <el-input v-model="setupForm.emailCode" maxlength="6" placeholder="输入邮件中的 6 位验证码" />
        </label>
        <label class="field">
          <span class="field-label">登录密码</span>
          <el-input v-model="setupForm.password" type="password" show-password maxlength="128" placeholder="至少 8 位" />
        </label>
        <label class="field">
          <span class="field-label">确认密码</span>
          <el-input v-model="setupForm.confirmPassword" type="password" show-password maxlength="128" placeholder="再次输入密码" @keyup.enter="submit" />
        </label>
        <p v-if="setupError" class="error-box" role="alert">{{ setupError }}</p>
        <el-button type="primary" class="submit-btn" :loading="settingUp" @click="submit">激活并进入控制台</el-button>
        <p class="foot-note">
          <router-link to="/login">← 返回登录</router-link>
        </p>
      </template>
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
  width: 460px;
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
.card .page-subtitle { margin-bottom: 18px; }
.field { display: block; margin-bottom: 14px; }
.field-label { display: block; font-size: 12px; font-weight: 700; margin-bottom: 7px; color: var(--muted); }
.email-row { display: flex; gap: 8px; }
.email-row .el-input { flex: 1; min-width: 0; }
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
  margin-top: 14px;
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
