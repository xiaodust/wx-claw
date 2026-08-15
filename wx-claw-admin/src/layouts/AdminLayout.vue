<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { changeAdminPassword } from '../api/admin'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const pwdVisible = ref(false)
const savingPwd = ref(false)
const pwdError = ref('')
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

function logout() { auth.logout(); router.replace('/login') }

function openPwd() {
  pwdError.value = ''
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdVisible.value = true
}

async function submitPwd() {
  pwdError.value = ''
  if (!pwdForm.oldPassword || !pwdForm.newPassword) { pwdError.value = '请输入旧密码和新密码'; return }
  if (pwdForm.newPassword.length < 8) { pwdError.value = '新密码至少 8 位'; return }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) { pwdError.value = '两次输入的新密码不一致'; return }
  savingPwd.value = true
  try {
    await changeAdminPassword(pwdForm.oldPassword, pwdForm.newPassword)
    pwdVisible.value = false
    ElMessage.success('密码已修改，请重新登录')
    auth.logout()
    router.replace('/login')
  } catch (e: any) {
    const status = e?.response?.status
    pwdError.value = status === 401 ? '当前密码不正确' : (e?.response?.data?.message || '修改失败，请稍后再试')
  } finally {
    savingPwd.value = false
  }
}
</script>

<template>
  <el-container class="shell">
    <el-aside width="230px" class="aside">
      <div class="brand">
        <span class="mark">W</span>
        <div><strong>WX-Claw</strong><small>运行管理中心</small></div>
      </div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item index="/overview">运行总览</el-menu-item>
        <el-menu-item index="/bots">Bot 状态</el-menu-item>
        <el-menu-item index="/conversations">对话与调用</el-menu-item>
        <el-menu-item index="/invite-codes">注册邀请码</el-menu-item>
      </el-menu>
      <button class="logout" @click="openPwd">修改密码</button>
      <button class="logout" @click="logout">退出管理端</button>
    </el-aside>
    <el-main class="main"><router-view /></el-main>

    <el-dialog v-model="pwdVisible" title="修改管理员密码" width="420px">
      <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="当前密码" />
      <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="新密码（至少 8 位）" style="margin-top:12px" />
      <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="确认新密码" style="margin-top:12px" @keyup.enter="submitPwd" />
      <p v-if="pwdError" class="pwd-err" role="alert">{{ pwdError }}</p>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPwd" @click="submitPwd">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.shell{min-height:100vh}
.aside{position:fixed;inset:0 auto 0 0;background:#101828;color:#fff;padding:24px 14px;display:flex;flex-direction:column}
.brand{display:flex;gap:12px;align-items:center;padding:4px 8px 28px}
.mark{display:grid;place-items:center;width:40px;height:40px;border-radius:12px;background:#7f56d9;font-weight:800}
.brand strong,.brand small{display:block}
.brand small{color:#98a2b3;margin-top:3px}
.menu{border:0;background:transparent}
.menu :deep(.el-menu-item){color:#d0d5dd;border-radius:9px;margin:4px 0}
.menu :deep(.el-menu-item:hover),.menu :deep(.is-active){background:#344054;color:#fff}
.logout{margin-top:8px;background:transparent;color:#98a2b3;border:1px solid #344054;border-radius:9px;padding:10px;cursor:pointer}
.logout:first-of-type{margin-top:auto}
.pwd-err{margin:12px 0 0;color:#d92d20;font-size:13px}
.main{margin-left:230px;padding:34px 38px;min-height:100vh}
</style>
