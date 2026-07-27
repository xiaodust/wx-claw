<script setup lang="ts">
import { computed } from 'vue'
const props=defineProps<{value?:string}>()
const formatted=computed(()=>{ if(!props.value)return '（无内容）'; try{return JSON.stringify(JSON.parse(props.value),null,2)}catch{return props.value} })
async function copy(){ await navigator.clipboard.writeText(formatted.value) }
</script>
<template><div><div class="payload-actions"><el-button size="small" @click="copy">复制</el-button></div><pre class="payload">{{formatted}}</pre></div></template>
<style scoped>.payload-actions{display:flex;justify-content:flex-end;margin-bottom:7px}</style>
