<script setup lang="ts">
import { computed } from 'vue'
const props=defineProps<{status:string}>()
const type=computed(()=>({ONLINE:'success',WAITING_QR:'warning',RECONNECTING:'warning',ERROR:'danger',OFFLINE:'info',STOPPED:'info'}[props.status] || 'primary'))
const text=computed(()=>({ONLINE:'在线',WAITING_QR:'等待扫码',RECONNECTING:'重连中',ERROR:'错误',OFFLINE:'离线',STOPPED:'已停止',STARTING:'启动中'}[props.status] || props.status))
</script>
<template><el-tag :type="type as any" effect="light" round>{{text}}</el-tag></template>
