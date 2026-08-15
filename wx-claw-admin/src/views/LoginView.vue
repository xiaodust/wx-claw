<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getOverview } from '../api/admin'
import { adminLogin } from '../api/public'

const key = ref('')
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()
const activeTab = ref<'account' | 'apikey'>('account')
const account = reactive({ username: '', password: '' })
const errorMsg = ref('')

async function submit() {
  if (loading.value) return
  errorMsg.value = ''
  loading.value = true
  try {
    if (activeTab.value === 'account') {
      if (!account.username.trim() || !account.password) {
        errorMsg.value = '请输入管理员用户名和密码'
        return
      }
      const result = await adminLogin(account.username.trim(), account.password)
      auth.login(result.sessionToken)
    } else {
      if (!key.value.trim()) {
        errorMsg.value = '请输入 API Key'
        return
      }
      auth.login(key.value.trim())
    }
    await getOverview()
    router.replace('/overview')
  } catch (e: any) {
    auth.logout()
    const status = e?.response?.status
    const message = e?.response?.data?.message
    if (status === 401) errorMsg.value = activeTab.value === 'account' ? '用户名或密码错误' : 'API Key 无效或缺少管理权限'
    else if (status === 429) errorMsg.value = '尝试过于频繁，请稍后再试'
    else errorMsg.value = message || '登录失败，请稍后再试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login">
    <section class="card">
      <div class="logo">W</div>
      <h1>WX-Claw 管理端</h1>
      <p>查看 Bot 运行状态、对话历史与模型调用链</p>

      <el-tabs v-model="activeTab" class="tabs">
        <el-tab-pane label="管理员账号" name="account">
          <el-input v-model="account.username" size="large" placeholder="管理员用户名" @keyup.enter="submit" />
          <el-input v-model="account.password" type="password" show-password size="large" placeholder="密码" @keyup.enter="submit" style="margin-top:12px" />
        </el-tab-pane>
        <el-tab-pane label="API Key" name="apikey">
          <el-input v-model="key" type="password" show-password size="large" placeholder="credentialId.secret" @keyup.enter="submit" />
        </el-tab-pane>
      </el-tabs>

      <p v-if="errorMsg" class="err" role="alert">{{ errorMsg }}</p>
      <el-button type="primary" size="large" :loading="loading" @click="submit">进入管理端</el-button>
      <small>登录凭据仅保存在当前浏览器标签页中</small>
    </section>
  </main>
</template>

<style scoped>
.login{min-height:100vh;display:grid;place-items:center;background:radial-gradient(circle at 20% 10%,#d9d6fe,transparent 35%),#f2f4f7}
.card{width:440px;padding:42px;background:#fff;border:1px solid #eaecf0;border-radius:20px;box-shadow:0 20px 60px rgba(16,24,40,.12);text-align:center}
.logo{display:grid;place-items:center;margin:auto;width:52px;height:52px;border-radius:15px;background:#7f56d9;color:#fff;font-size:25px;font-weight:800}
h1{margin:18px 0 8px}
p{color:#667085;margin:0 0 28px}
.tabs :deep(.el-tabs__item){font-weight:600}
.err{margin:12px 0 0;color:#d92d20;font-size:13px;text-align:left}
.el-button{width:100%;margin-top:14px}
small{display:block;color:#98a2b3;margin-top:18px}
</style>
