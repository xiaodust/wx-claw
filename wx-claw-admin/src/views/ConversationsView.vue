<script setup lang="ts">
import { onMounted,reactive,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { getBots,getConversations } from '../api/admin'
import type { BotStatus,Conversation } from '../types/admin'
const route=useRoute(),router=useRouter(),rows=ref<Conversation[]>([]),bots=ref<BotStatus[]>([]),loading=ref(false),total=ref(0)
const filters=reactive({botId:String(route.query.botId||''),keyword:'',active:undefined as boolean|undefined,page:0,size:20})
async function load(){loading.value=true;try{const r=await getConversations(filters);rows.value=r.content;total.value=r.totalElements}finally{loading.value=false}}
function search(){filters.page=0;load()} function pageChange(v:number){filters.page=v-1;load()} const fmt=(v?:string)=>v?new Date(v).toLocaleString():'—'
function openRow(row:Conversation){router.push('/conversations/'+row.id)}
onMounted(async()=>{bots.value=await getBots();load()})
</script>
<template><div><h1 class="page-title">对话与调用</h1><p class="page-subtitle">按 Bot、用户或 Session 查询完整消息与模型调用链</p><section class="panel"><div class="toolbar"><el-select v-model="filters.botId" placeholder="全部 Bot" clearable style="width:210px"><el-option v-for="b in bots" :key="b.tenantId+b.botId" :value="b.botId" :label="b.displayName+' / '+b.botId"/></el-select><el-input v-model="filters.keyword" placeholder="用户或 Session" clearable style="width:260px" @keyup.enter="search"/><el-select v-model="filters.active" placeholder="全部会话" clearable style="width:150px"><el-option :value="true" label="活跃"/><el-option :value="false" label="已归档"/></el-select><el-button type="primary" @click="search">查询</el-button></div><el-table :data="rows" v-loading="loading" @row-click="openRow" row-class-name="clickable"><el-table-column prop="internalUserId" label="用户" show-overflow-tooltip/><el-table-column prop="botId" label="Bot"/><el-table-column prop="sessionId" label="Session" show-overflow-tooltip/><el-table-column prop="messageCount" label="消息" width="75"/><el-table-column label="状态" width="90"><template #default="s"><el-tag :type="s.row.active?'success':'info'">{{s.row.active?'活跃':'归档'}}</el-tag></template></el-table-column><el-table-column label="最后消息" width="180"><template #default="s">{{fmt(s.row.lastMessageTime)}}</template></el-table-column></el-table><el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :page-size="filters.size" :current-page="filters.page+1" @current-change="pageChange"/></section></div></template>
<style scoped>.pager{justify-content:flex-end;margin-top:18px}:deep(.clickable){cursor:pointer}</style>
