<script setup lang="ts">
import { computed } from 'vue'
import { renderReport, reportDocument } from '../reportHtml'

const props = defineProps<{ title: string; markdown: string }>()
const html = computed(() => renderReport(props.markdown || ''))

function fileName() {
  const base = (props.title || '报告').replace(/[\\/:*?"<>|]/g, '').trim() || '报告'
  return base + '.html'
}

function download() {
  const blob = new Blob([reportDocument(props.title, props.markdown || '')], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName()
  a.click()
  URL.revokeObjectURL(url)
}

function printDoc() {
  const frame = document.createElement('iframe')
  frame.setAttribute('style', 'position:fixed;right:0;bottom:0;width:0;height:0;border:0')
  document.body.appendChild(frame)
  const doc = frame.contentDocument
  if (!doc) { frame.remove(); return }
  doc.open()
  doc.write(reportDocument(props.title, props.markdown || ''))
  doc.close()
  frame.contentWindow?.focus()
  frame.contentWindow?.print()
  window.setTimeout(() => frame.remove(), 1000)
}
</script>

<template>
  <div v-if="html">
    <div class="report-tools">
      <button class="btn secondary" type="button" @click="download">导出 HTML</button>
      <button class="btn secondary" type="button" @click="printDoc">打印</button>
    </div>
    <article class="report-sheet" v-html="html"></article>
  </div>
</template>
