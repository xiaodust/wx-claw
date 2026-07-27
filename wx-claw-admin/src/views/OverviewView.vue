<script setup lang="ts">
import { onBeforeUnmount,onMounted,ref } from 'vue'
import { getBots,getOverview } from '../api/admin'
import type { BotStatus,Overview } from '../types/admin'
import BotStatusTag from '../components/BotStatusTag.vue'
const data=ref<Overview>(),bots=ref<BotStatus[]>([]),loading=ref(false);let timer:number|undefined
async function load(){if(loading.value||document.hidden)return;loading.value=true;try{data.value=await getOverview();bots.value=await getBots()}finally{loading.value=false}}
function visibilityChanged(){if(!document.hidden)load()}
onMounted(()=>{load();timer=window.setInterval(load,5000);document.addEventListener('visibilitychange',visibilityChanged)});onBeforeUnmount(()=>{clearInterval(timer);document.removeEventListener('visibilitychange',visibilityChanged)})
const fmt=(v?:string)=>v?new Date(v).toLocaleString():'—'
</script>
<template><div><h1 class="page-title">运行总览</h1><p class="page-subtitle">实时观察 Bot 连接状态与今日模型调用情况</p><el-row :gutter="16" v-loading="loading"><el-col v-for="item in [ ['Bot 总数',data?.botCount],['在线',data?.onlineBotCount],['等待扫码',data?.waitingQrBotCount],['异常',data?.errorBotCount],['今日对话',data?.todayConversationCount],['今日模型调用',data?.todayInvocationCount],['调用失败',data?.todayFailedInvocationCount] ]" :key="item[0]" :span="6"><div class="metric"><span>{{item[0]}}</span><strong>{{item[1]??0}}</strong></div></el-col></el-row><section class="panel table"><h2>Bot 实时状态</h2><el-table :data="bots"><el-table-column prop="displayName" label="Bot"/><el-table-column prop="tenantId" label="租户"/><el-table-column label="状态"><template #default="s"><BotStatusTag :status="s.row.runtimeStatus"/></template></el-table-column><el-table-column label="最后轮询"><template #default="s">{{fmt(s.row.lastPollAt)}}</template></el-table-column><el-table-column prop="reconnectAttempts" label="重连次数" width="100"/><el-table-column prop="lastError" label="最近错误" show-overflow-tooltip/></el-table></section></div></template>
<style scoped>.metric{background:#fff;border:1px solid #e7ecf2;border-radius:14px;padding:20px;margin-bottom:16px}.metric span{display:block;color:#667085}.metric strong{display:block;font-size:30px;margin-top:10px}.table{margin-top:10px}.table h2{font-size:17px;margin:0 0 16px}</style>
