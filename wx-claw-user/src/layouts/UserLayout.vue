<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getAccountInfo } from '../api/user'
import type { AccountInfo } from '../types/user'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const needsAccountSetup = ref(false)
const accountInfo = ref<AccountInfo | null>(null)

const chipLabel = computed(() => {
  if (accountInfo.value?.username) return accountInfo.value.username
  const key = authStore.token
  return key.includes('.') ? key.split('.')[0] : '—'
})

onMounted(async () => {
  try {
    const info = await getAccountInfo()
    accountInfo.value = info
    needsAccountSetup.value = !info.hasAccount
  } catch {
    needsAccountSetup.value = false
  }
})

function activePath() {
  if (route.path.startsWith('/bots/')) return '/bots'
  return route.path
}

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <div class="accent-rail" aria-hidden="true"></div>
    <div v-if="needsAccountSetup" class="setup-banner">
      当前租户还没有登录账号，<router-link to="/settings">点此完善用户名 / 邮箱 / 密码</router-link>，之后即可用账号密码登录。
    </div>
    <header class="header">
      <router-link class="brand" to="/">
        <span class="brand-mark">WX</span>
        <span class="brand-name">CLAW <span class="brand-sub">用户控制台</span></span>
      </router-link>
      <el-menu mode="horizontal" :default-active="activePath()" router class="nav">
        <el-menu-item index="/bots">BOT 管理</el-menu-item>
        <el-menu-item index="/settings">KEY / 模型设置</el-menu-item>
      </el-menu>
      <div class="header-right">
        <span class="tenant-chip mono">tenant: {{ chipLabel }}</span>
        <button class="logout-btn" type="button" @click="logout">退出</button>
      </div>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.layout { min-height: 100vh; }
.accent-rail { height: 3px; background: linear-gradient(90deg, var(--accent), var(--accent-2)); }
.setup-banner {
  padding: 8px 26px;
  background: rgba(255, 180, 0, 0.12);
  border-bottom: 1px solid rgba(255, 180, 0, 0.3);
  color: #e8c066;
  font-size: 13px;
  text-align: center;
}
.setup-banner a { color: var(--accent); font-weight: 700; }
.header {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 26px;
  padding: 0 26px;
  height: 60px;
  background: rgba(11, 13, 16, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--line);
}
.brand { display: flex; align-items: center; gap: 10px; text-decoration: none; color: var(--fg); }
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
  letter-spacing: 0.5px;
}
.brand-name { font-weight: 800; letter-spacing: 2px; font-size: 14px; white-space: nowrap; }
.brand-sub { color: var(--muted); font-weight: 500; letter-spacing: 0; font-size: 12px; margin-left: 4px; }
.nav { flex: 1; border-bottom: none; }
.nav :deep(.el-menu-item) { font-weight: 700; font-size: 13px; letter-spacing: 0.5px; }
.nav :deep(.el-menu-item.is-active) {
  color: var(--accent);
  border-bottom-color: var(--accent);
}
.nav :deep(.el-menu-item:hover) { color: var(--fg); }
.header-right { display: flex; align-items: center; gap: 14px; }
.tenant-chip {
  font-size: 11px;
  color: var(--accent-2);
  border: 1px solid var(--line);
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(45, 225, 194, 0.06);
}
.logout-btn {
  padding: 7px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
  transition: color 180ms, border-color 180ms;
}
.logout-btn:hover { color: var(--danger); border-color: rgba(255, 107, 107, 0.5); }
.main { max-width: 1100px; margin: 0 auto; padding: 26px 24px 48px; }

@media (max-width: 760px) {
  .tenant-chip { display: none; }
  .header { gap: 14px; padding: 0 14px; }
}
</style>
