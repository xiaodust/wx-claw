<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const route=useRoute(), router=useRouter(), auth=useAuthStore()
function logout(){ auth.logout(); router.replace('/login') }
</script>
<template>
  <el-container class="shell">
    <el-aside width="230px" class="aside">
      <div class="brand"><span class="mark">W</span><div><strong>WX-Claw</strong><small>运行管理中心</small></div></div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item index="/overview">运行总览</el-menu-item>
        <el-menu-item index="/bots">Bot 状态</el-menu-item>
        <el-menu-item index="/conversations">对话与调用</el-menu-item>
      </el-menu>
      <button class="logout" @click="logout">退出管理端</button>
    </el-aside>
    <el-main class="main"><router-view /></el-main>
  </el-container>
</template>
<style scoped>
.shell{min-height:100vh}.aside{position:fixed;inset:0 auto 0 0;background:#101828;color:#fff;padding:24px 14px;display:flex;flex-direction:column}.brand{display:flex;gap:12px;align-items:center;padding:4px 8px 28px}.mark{display:grid;place-items:center;width:40px;height:40px;border-radius:12px;background:#7f56d9;font-weight:800}.brand strong,.brand small{display:block}.brand small{color:#98a2b3;margin-top:3px}.menu{border:0;background:transparent}.menu :deep(.el-menu-item){color:#d0d5dd;border-radius:9px;margin:4px 0}.menu :deep(.el-menu-item:hover),.menu :deep(.is-active){background:#344054;color:#fff}.logout{margin-top:auto;background:transparent;color:#98a2b3;border:1px solid #344054;border-radius:9px;padding:10px;cursor:pointer}.main{margin-left:230px;padding:34px 38px;min-height:100vh}
</style>
