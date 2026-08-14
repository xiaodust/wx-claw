<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

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
    <header class="header">
      <div class="brand">WX-Claw 用户控制台</div>
      <el-menu mode="horizontal" :default-active="activePath()" router class="nav">
        <el-menu-item index="/bots">我的 Bot</el-menu-item>
        <el-menu-item index="/settings">API Key 设置</el-menu-item>
      </el-menu>
      <el-button text @click="logout">退出</el-button>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.layout { min-height: 100vh; }
.header { display: flex; align-items: center; gap: 24px; padding: 0 24px; background: #fff; border-bottom: 1px solid #e7ecf2; }
.brand { font-weight: 700; font-size: 16px; }
.nav { flex: 1; border-bottom: none; }
.main { max-width: 1080px; margin: 0 auto; padding: 24px; }
</style>
