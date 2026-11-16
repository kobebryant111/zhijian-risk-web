<script setup lang="ts">
import { ref } from 'vue'
import { fileToImportText } from '../fileImport'

const props = defineProps<{ multiple?: boolean; heading: string; hint: string }>()
const text = defineModel<string>({ required: true })
const files = ref<{ name: string; chars: number }[]>([])
const drag = ref(false)
const err = ref('')
const note = ref('')
const input = ref<HTMLInputElement | null>(null)

const accept = '.docx,.xlsx,.xls,.csv,.txt,.md,.json'

async function load(list: FileList | File[]) {
  const picked = Array.from(list)
  if (!picked.length) return
  err.value = ''
  note.value = ''
  const parts: string[] = []
  const meta: { name: string; chars: number }[] = []
  for (const file of props.multiple ? picked : picked.slice(0, 1)) {
    try {
      const body = await fileToImportText(file)
      parts.push(props.multiple ? `【财报附件：${file.name}】\n${body}` : body)
      meta.push({ name: file.name, chars: body.length })
    } catch (e) {
      err.value = e instanceof Error ? e.message : String(e)
      return
    }
  }
  text.value = parts.join('\n\n────────\n\n')
  files.value = meta
  const total = meta.reduce((n, f) => n + f.chars, 0)
  const size = `约 ${Math.round(total / 1000)} 千字`
  if (props.multiple) {
    note.value = total > 40000
      ? `已载入 ${meta.length} 个附件，${size}。评审只取前 4 万字。`
      : `已载入 ${meta.length} 个附件，${size}，将与全网搜、工商信息一并分析`
  } else {
    note.value = `已载入「${meta[0].name}」（${size}），可开始分析`
  }
}

function clear() {
  text.value = ''
  files.value = []
  note.value = ''
  err.value = ''
}

function onDrop(e: DragEvent) {
  drag.value = false
  if (e.dataTransfer?.files?.length) void load(e.dataTransfer.files)
}
</script>

<template>
  <div
    class="dropzone"
    :class="{ over: drag }"
    @dragover.prevent="drag = true"
    @dragleave="drag = false"
    @drop.prevent="onDrop"
  >
    <div class="drop-title">{{ heading }}</div>
    <div class="muted drop-hint">{{ hint }}</div>
    <div v-if="files.length" class="drop-files">
      <div v-for="f in files" :key="f.name">已选：{{ f.name }} · {{ f.chars.toLocaleString() }} 字</div>
    </div>
    <div class="drop-actions">
      <button class="btn secondary" type="button" @click="input?.click()">{{ files.length ? '重新选择附件' : '选择附件' }}</button>
      <button v-if="files.length" class="btn secondary" type="button" @click="clear">清除附件</button>
    </div>
    <p v-if="note" class="drop-note">{{ note }}</p>
    <p v-if="err" class="drop-err">{{ err }}</p>
    <input
      ref="input"
      type="file"
      :multiple="multiple"
      :accept="accept"
      hidden
      @change="(e) => { const t = e.target as HTMLInputElement; if (t.files?.length) void load(t.files); t.value = '' }"
    />
  </div>
</template>
