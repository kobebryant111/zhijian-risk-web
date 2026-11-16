<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ busy: boolean }>()
const emit = defineEmits<{ download: []; file: [File] }>()
const input = ref<HTMLInputElement | null>(null)

function pick() {
  input.value?.click()
}

function onChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (file) emit('file', file)
}
</script>

<template>
  <span class="excel-actions">
    <button class="btn secondary" type="button" :disabled="busy" @click="emit('download')">下载模板</button>
    <button class="btn secondary" type="button" :disabled="busy" @click="pick">{{ busy ? '导入中' : '导入' }}</button>
    <input ref="input" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" hidden @change="onChange" />
  </span>
</template>

<style scoped>
.excel-actions { display: contents; }
</style>
