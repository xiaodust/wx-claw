<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getOverview } from '../api/admin'
const key=ref(''),loading=ref(false),router=useRouter(),auth=useAuthStore()
async function submit(){ if(!key.value.trim())return; loading.value=true; auth.login(key.value.trim()); try{await getOverview();router.replace('/overview')}catch{auth.logout();ElMessage.error('API Key 无效或缺少管理权限')}finally{loading.value=false} }
</script>
<template><main class="login"><section class="card"><div class="logo">W</div><h1>WX-Claw 管理端</h1><p>查看 Bot 运行状态、对话历史与模型调用链</p><el-input v-model="key" type="password" show-password size="large" placeholder="credentialId.secret" @keyup.enter="submit"/><el-button type="primary" size="large" :loading="loading" @click="submit">进入管理端</el-button><small>密钥仅保存在当前浏览器标签页中</small></section></main></template>
<style scoped>.login{min-height:100vh;display:grid;place-items:center;background:radial-gradient(circle at 20% 10%,#d9d6fe,transparent 35%),#f2f4f7}.card{width:440px;padding:42px;background:#fff;border:1px solid #eaecf0;border-radius:20px;box-shadow:0 20px 60px rgba(16,24,40,.12);text-align:center}.logo{display:grid;place-items:center;margin:auto;width:52px;height:52px;border-radius:15px;background:#7f56d9;color:#fff;font-size:25px;font-weight:800}h1{margin:18px 0 8px}p{color:#667085;margin:0 0 28px}.el-button{width:100%;margin-top:14px}small{display:block;color:#98a2b3;margin-top:18px}</style>
