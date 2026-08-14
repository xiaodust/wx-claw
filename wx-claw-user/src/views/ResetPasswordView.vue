<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { resetPassword } from '../api/public'

const route = useRoute()
const token = (route.query.token as string) || ''

const form = reactive({ password: '', confirmPassword: '' })
const loading = ref(false)
const done = ref(false)
const errorMsg = ref('')

const hasToken = computed(() => Boolean(token))
const passwordOk = computed(() => form.password.length >= 8 && form.password === form.confirmPassword)

async function submit() {
  if (!passwordOk.value || loading.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    await resetPassword({ token, newPassword: form.password })
    done.value = true
  } catch (e: unknown) {
    const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    errorMsg.value = message || '重置失败，链接可能已失效，请重新申请'
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
      <p class="page-kicker">SET NEW PASSWORD</p>
      <h1 class="page-title">设置新密码</h1>
      <p class="page-subtitle">重置链接 30 分钟内有效，设置后旧密码与已登录会话全部失效</p>

      <div v-if="!hasToken" class="error-box">
        链接缺少重置令牌，请重新从邮件中打开完整链接，或
        <router-link to="/forgot-password">重新申请</router-link>。
      </div>

      <template v-else-if="!done">
        <label class="field">
          <span class="field-label">新密码</span>
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="128"
            autocomplete="new-password"
            placeholder="至少 8 位"
            @keyup.enter="submit"
          />
        </label>
        <label class="field">
          <span class="field-label">确认新密码</span>
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            maxlength="128"
            autocomplete="new-password"
            placeholder="再次输入新密码"
            @keyup.enter="submit"
          />
        </label>
        <p v-if="form.confirmPassword && !passwordOk" class="hint-error">
          {{ form.password.length < 8 ? '密码至少 8 位' : '两次输入的密码不一致' }}
        </p>
        <p v-if="errorMsg" class="error-box" role="alert">{{ errorMsg }}</p>
        <el-button type="primary" class="submit-btn" :disabled="!passwordOk" :loading="loading" @click="submit">
          重置密码
        </el-button>
      </template>

      <div v-else class="ok-box">
        <b>密码已重置</b>
        <p>请使用新密码登录控制台。所有旧会话已失效。</p>
        <router-link class="back-login" to="/login">去登录 →</router-link>
      </div>
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
  line-height: 1.7;
}
.error-box a { color: var(--accent); }
.hint-error { margin: 0 0 12px; color: var(--danger); font-size: 12px; }
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
</style>
