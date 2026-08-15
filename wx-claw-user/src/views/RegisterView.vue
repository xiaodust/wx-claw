<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { registerTenant, sendEmailCode } from '../api/public'
import { useAuthStore } from '../stores/auth'
import type { RegisterTenantResult } from '../types/user'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ tenantName: '', tenantCode: '', contactEmail: '', emailCode: '', username: '', password: '', confirmPassword: '', inviteCode: '' })
const fieldErrors = reactive<Record<string, string>>({})
const submitting = ref(false)
const serverError = ref('')
const result = ref<RegisterTenantResult | null>(null)
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer: number | undefined

const codePattern = /^[a-z0-9][a-z0-9-]{0,31}$/
const emailPattern = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const usernamePattern = /^[a-z0-9_-]{3,32}$/

const canSubmit = computed(() =>
  form.tenantName.trim().length >= 2
  && form.username.trim().length >= 3
  && form.password.length >= 8
  && form.inviteCode.trim().length >= 4
  && form.contactEmail.trim().length >= 5
  && form.emailCode.trim().length >= 4
  && !Object.values(fieldErrors).some(Boolean))

function validateField(key: 'tenantName' | 'tenantCode' | 'contactEmail' | 'emailCode' | 'username' | 'password' | 'confirmPassword' | 'inviteCode') {
  fieldErrors[key] = ''
  if (key === 'tenantName') {
    const name = form.tenantName.trim()
    if (!name) fieldErrors.tenantName = '请输入租户名称'
    else if (name.length > 50) fieldErrors.tenantName = '租户名称不能超过 50 个字符'
  }
  if (key === 'tenantCode' && form.tenantCode.trim()) {
    if (!codePattern.test(form.tenantCode.trim())) {
      fieldErrors.tenantCode = '仅支持小写字母、数字和连字符，以字母或数字开头（2-32 位）'
    }
  }
  if (key === 'contactEmail') {
    const email = form.contactEmail.trim()
    if (!email) fieldErrors.contactEmail = '邮箱为必填项'
    else if (!emailPattern.test(email)) fieldErrors.contactEmail = '邮箱格式不正确'
  }
  if (key === 'emailCode' && form.emailCode.trim().length < 4) {
    fieldErrors.emailCode = '请输入邮箱验证码'
  }
  if (key === 'username') {
    const username = form.username.trim()
    if (username && !usernamePattern.test(username)) {
      fieldErrors.username = '用户名需为 3-32 位，仅支持小写字母、数字、下划线、连字符'
    }
  }
  if (key === 'password' && form.password && form.password.length < 8) {
    fieldErrors.password = '密码至少 8 位'
  }
  if (key === 'confirmPassword' && form.confirmPassword && form.confirmPassword !== form.password) {
    fieldErrors.confirmPassword = '两次输入的密码不一致'
  }
  if (key === 'inviteCode' && form.inviteCode.trim().length < 4) {
    fieldErrors.inviteCode = '请输入注册邀请码'
  }
}

async function submit() {
  if (!canSubmit.value || submitting.value) return
  validateField('tenantName')
  validateField('tenantCode')
  validateField('contactEmail')
  validateField('emailCode')
  validateField('username')
  validateField('password')
  validateField('confirmPassword')
  validateField('inviteCode')
  if (Object.values(fieldErrors).some(Boolean)) return

  submitting.value = true
  serverError.value = ''
  try {
    result.value = await registerTenant({
      tenantName: form.tenantName.trim(),
      tenantCode: form.tenantCode.trim() || undefined,
      contactEmail: form.contactEmail.trim().toLowerCase(),
      username: form.username.trim().toLowerCase(),
      password: form.password,
      inviteCode: form.inviteCode.trim().toUpperCase(),
      emailCode: form.emailCode.trim(),
    })
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    if (status === 429) serverError.value = '注册太频繁，请稍后再试。'
    else if (message) serverError.value = message
    else serverError.value = '注册失败，请稍后再试。'
  } finally {
    submitting.value = false
  }
}

async function sendCode() {
  if (sendingCode.value || countdown.value > 0) return
  validateField('contactEmail')
  if (fieldErrors.contactEmail) return
  sendingCode.value = true
  serverError.value = ''
  try {
    await sendEmailCode({ email: form.contactEmail.trim().toLowerCase(), purpose: 'REGISTER' })
    countdown.value = 60
    countdownTimer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && countdownTimer) {
        window.clearInterval(countdownTimer)
        countdownTimer = undefined
      }
    }, 1000)
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    serverError.value = status === 429 ? '发送太频繁，请稍后再试' : (message || '验证码发送失败，请稍后再试')
  } finally {
    sendingCode.value = false
  }
}

function enterConsole() {
  if (!result.value) return
  const token = result.value.sessionToken
  if (token) {
    authStore.login(token)
    router.push('/bots')
  } else {
    router.push('/login')
  }
}
</script>

<template>
  <div class="register-page">
    <div class="bg-grid" aria-hidden="true"></div>

    <header class="reg-header">
      <router-link class="brand" to="/">
        <span class="brand-mark">WX</span>
        <span class="brand-name">CLAW</span>
      </router-link>
      <router-link class="back-link" to="/">← 返回首页</router-link>
    </header>

    <main class="reg-main">
      <div v-if="!result" class="reg-card">
        <p class="kicker"><span class="kicker-dot"></span> TENANT SIGNUP</p>
        <h1>注册租户</h1>
        <p class="sub">需要邀请码才能注册；注册成功后即可用账号密码登录控制台。</p>

        <form novalidate @submit.prevent="submit">
          <label class="field">
            <span class="field-label">注册邀请码 <i>*</i></span>
            <input
              v-model="form.inviteCode"
              type="text"
              maxlength="32"
              autocomplete="off"
              placeholder="向平台申请获取邀请码"
              :class="{ invalid: fieldErrors.inviteCode }"
              @blur="validateField('inviteCode')"
            />
            <span v-if="fieldErrors.inviteCode" class="field-error">{{ fieldErrors.inviteCode }}</span>
          </label>

          <label class="field">
            <span class="field-label">租户名称 <i>*</i></span>
            <input
              v-model="form.tenantName"
              type="text"
              maxlength="50"
              placeholder="例如：我的工作室"
              :class="{ invalid: fieldErrors.tenantName }"
              @blur="validateField('tenantName')"
            />
            <span v-if="fieldErrors.tenantName" class="field-error">{{ fieldErrors.tenantName }}</span>
          </label>

          <label class="field">
            <span class="field-label">租户编码 <em>选填</em></span>
            <input
              v-model="form.tenantCode"
              type="text"
              maxlength="32"
              placeholder="my-org（小写字母/数字/连字符）"
              :class="{ invalid: fieldErrors.tenantCode }"
              @blur="validateField('tenantCode')"
            />
            <span v-if="fieldErrors.tenantCode" class="field-error">{{ fieldErrors.tenantCode }}</span>
          </label>

          <label class="field">
            <span class="field-label">联系邮箱 <i>*</i></span>
            <div class="email-row">
              <input
                v-model="form.contactEmail"
                type="email"
                maxlength="128"
                autocomplete="email"
                placeholder="ops@example.com（用于注册验证与密码找回）"
                :class="{ invalid: fieldErrors.contactEmail }"
                @blur="validateField('contactEmail')"
                @input="form.emailCode = ''"
              />
              <button
                type="button"
                class="code-btn"
                :disabled="sendingCode || countdown > 0 || !emailPattern.test(form.contactEmail.trim())"
                @click="sendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : (sendingCode ? '发送中…' : '发送验证码') }}
              </button>
            </div>
            <span v-if="fieldErrors.contactEmail" class="field-error">{{ fieldErrors.contactEmail }}</span>
          </label>

          <label class="field">
            <span class="field-label">邮箱验证码 <i>*</i></span>
            <input
              v-model="form.emailCode"
              type="text"
              maxlength="6"
              inputmode="numeric"
              autocomplete="one-time-code"
              placeholder="输入邮件中的 6 位验证码"
              :class="{ invalid: fieldErrors.emailCode }"
              @blur="validateField('emailCode')"
            />
            <span v-if="fieldErrors.emailCode" class="field-error">{{ fieldErrors.emailCode }}</span>
          </label>

          <label class="field">
            <span class="field-label">登录用户名 <i>*</i></span>
            <input
              v-model="form.username"
              type="text"
              maxlength="32"
              autocomplete="username"
              placeholder="小写字母/数字/下划线/连字符，3-32 位"
              :class="{ invalid: fieldErrors.username }"
              @blur="validateField('username')"
            />
            <span v-if="fieldErrors.username" class="field-error">{{ fieldErrors.username }}</span>
          </label>

          <label class="field">
            <span class="field-label">登录密码 <i>*</i></span>
            <input
              v-model="form.password"
              type="password"
              maxlength="128"
              autocomplete="new-password"
              placeholder="至少 8 位"
              :class="{ invalid: fieldErrors.password }"
              @blur="validateField('password')"
            />
            <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
          </label>

          <label class="field">
            <span class="field-label">确认密码 <i>*</i></span>
            <input
              v-model="form.confirmPassword"
              type="password"
              maxlength="128"
              autocomplete="new-password"
              placeholder="再次输入密码"
              :class="{ invalid: fieldErrors.confirmPassword }"
              @blur="validateField('confirmPassword')"
            />
            <span v-if="fieldErrors.confirmPassword" class="field-error">{{ fieldErrors.confirmPassword }}</span>
          </label>

          <p v-if="serverError" class="server-error" role="alert">{{ serverError }}</p>

          <button class="submit-btn" type="submit" :disabled="!canSubmit || submitting">
            {{ submitting ? '注册中…' : '注册并进入控制台' }}
          </button>
        </form>

        <p class="foot-note">
          已有账号？
          <router-link to="/login">用 API Key 登录</router-link>
        </p>
      </div>

      <div v-else class="reg-card success-card">
        <p class="kicker"><span class="kicker-dot"></span> REGISTERED ✓</p>
        <h1>注册成功</h1>
        <p class="sub">
          租户 <b>{{ result.tenantName }}</b>（{{ result.tenantCode }}）已创建，
          用户名 <b class="mono">{{ result.username }}</b> 可用于登录。
        </p>

        <div class="alert-box ok-box">
          <b>账号已就绪</b>：控制台支持用户名 + 密码登录，不再依赖一次性 API Key。
        </div>
        <button class="submit-btn" type="button" @click="enterConsole">进入控制台 →</button>
      </div>
    </main>

    <footer class="reg-footer">
      <span>WX-CLAW · 微信 ILink 智能体平台</span>
      <router-link to="/login">登录控制台</router-link>
    </footer>
  </div>
</template>

<style scoped>
.register-page {
  --bg: #0b0d10;
  --panel: #141821;
  --line: rgba(255, 255, 255, 0.1);
  --fg: #f2f4f8;
  --muted: #9aa3b2;
  --accent: #ffb400;
  --danger: #ff6b6b;
  min-height: 100vh;
  background: var(--bg);
  color: var(--fg);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow-x: hidden;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}
.mono { font-family: "JetBrains Mono", Consolas, monospace; }
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
.reg-header {
  position: relative;
  max-width: 560px;
  width: 100%;
  margin: 0 auto;
  padding: 28px 24px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.brand { display: flex; align-items: center; gap: 8px; text-decoration: none; color: var(--fg); }
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
.brand-name { font-weight: 800; letter-spacing: 2px; }
.back-link { color: var(--muted); text-decoration: none; font-size: 13px; }
.back-link:hover { color: var(--accent); }

.reg-main {
  position: relative;
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 40px 24px 64px;
}
.reg-card {
  width: 100%;
  max-width: 520px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.025), transparent 35%), var(--panel);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 34px 32px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.45);
}
.kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #2de1c2;
}
.kicker-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 10px var(--accent); }
h1 { margin: 0 0 8px; font-size: 28px; letter-spacing: -0.5px; }
.sub { margin: 0 0 26px; color: var(--muted); font-size: 13px; line-height: 1.7; }
.sub b { color: var(--fg); }

.field { display: block; margin-bottom: 18px; }
.field-label { display: flex; gap: 8px; align-items: center; font-size: 13px; font-weight: 700; margin-bottom: 8px; }
.field-label i { color: var(--danger); font-style: normal; }
.field-label em { color: var(--muted); font-style: normal; font-weight: 400; font-size: 12px; }
.field input {
  width: 100%;
  padding: 12px 14px;
  background: rgba(11, 13, 16, 0.7);
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--fg);
  font-size: 14px;
  font-family: inherit;
  transition: border-color 180ms, box-shadow 180ms;
}
.field input::placeholder { color: #5b6472; }
.field input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(255, 180, 0, 0.14); }
.field input.invalid { border-color: var(--danger); }
.field-error { display: block; margin-top: 6px; color: var(--danger); font-size: 12px; }
.email-row { display: flex; gap: 8px; }
.email-row input { flex: 1; min-width: 0; }
.code-btn {
  flex-shrink: 0;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--accent);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 180ms, color 180ms;
}
.code-btn:hover:not(:disabled) { border-color: var(--accent); }
.code-btn:disabled { opacity: 0.45; cursor: not-allowed; }

.server-error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid rgba(255, 107, 107, 0.4);
  border-radius: 8px;
  background: rgba(255, 107, 107, 0.08);
  color: var(--danger);
  font-size: 13px;
}
.submit-btn {
  width: 100%;
  padding: 13px;
  border: none;
  border-radius: 10px;
  background: var(--accent);
  color: #14161a;
  font-size: 15px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 180ms, box-shadow 180ms, opacity 180ms;
}
.submit-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(255, 180, 0, 0.25); }
.submit-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.submit-btn:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.foot-note { margin: 18px 0 0; text-align: center; color: var(--muted); font-size: 13px; }
.foot-note a { color: var(--accent); text-decoration: none; }

.success-card .kicker { color: #2de1c2; }
.key-block { border: 1px solid var(--line); border-radius: 12px; padding: 16px; background: rgba(11, 13, 16, 0.7); }
.key-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 12px; color: var(--muted); }
.key-warn { color: var(--accent); font-size: 11px; letter-spacing: 1px; }
.key-value {
  display: block;
  padding: 12px;
  background: #0b0d10;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
  color: #2de1c2;
  word-break: break-all;
  user-select: all;
}
.key-actions { display: flex; gap: 10px; margin-top: 14px; }
.btn {
  flex: 1;
  padding: 11px;
  border: none;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 180ms, box-shadow 180ms;
}
.btn:hover { transform: translateY(-1px); }
.copy-btn { background: var(--accent); color: #14161a; }
.ghost-btn { background: transparent; border: 1px solid var(--line); color: var(--fg); }
.alert-box {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 180, 0, 0.35);
  border-radius: 10px;
  background: rgba(255, 180, 0, 0.07);
  color: #e8c066;
  font-size: 12px;
  line-height: 1.7;
}
.alert-box.ok-box {
  border-color: rgba(45, 225, 194, 0.35);
  background: rgba(45, 225, 194, 0.07);
  color: #9fe8db;
}

.reg-footer {
  position: relative;
  max-width: 560px;
  width: 100%;
  margin: 0 auto;
  padding: 0 24px 26px;
  display: flex;
  justify-content: space-between;
  color: var(--muted);
  font-size: 12px;
}
.reg-footer a { color: var(--muted); text-decoration: none; }
.reg-footer a:hover { color: var(--accent); }
</style>
