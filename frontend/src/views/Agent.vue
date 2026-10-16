<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const list = ref<any[]>([])
const current = ref<any>(null)
const input = ref('')
const sending = ref(false)
let timer: number | undefined

const messages = computed(() => {
  try { return JSON.parse(current.value?.messagesJson || '[]') } catch { return [] }
})

async function loadList() {
  if (!auth.currentOrgId) return
  list.value = await api.listConversations()
}

async function create() {
  current.value = await api.createConversation()
  await loadList()
}

async function open(c: any) {
  current.value = await api.getConversation(c.id)
  if (current.value.status === 'running') poll()
}

async function send() {
  if (!current.value || !input.value.trim()) return
  sending.value = true
  try {
    const content = input.value.trim()
    input.value = ''
    current.value = await api.sendMessage(current.value.id, content)
    poll()
  } catch (e) { alert(e instanceof Error ? e.message : String(e)) }
  finally { sending.value = false }
}

function poll() {
  window.clearInterval(timer)
  timer = window.setInterval(async () => {
    if (!current.value?.id) return
    current.value = await api.getConversation(current.value.id)
    if (current.value.status !== 'running') {
      window.clearInterval(timer)
      await loadList()
    }
  }, 1200)
}

async function remove(id: number) {
  await api.deleteConversation(id)
  if (current.value?.id === id) current.value = null
  await loadList()
}

onMounted(async () => { await loadList(); if (!list.value.length) await create() })
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div>
    <div class="page-head">
      <div><h1>问鉴控</h1></div>
      <button class="btn" @click="create">新对话</button>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <div v-else class="split">
      <div class="panel">
        <div v-for="c in list" :key="c.id" class="list-item" :class="{active: current?.id===c.id}" @click="open(c)">
          <div style="display:flex;justify-content:space-between;gap:8px">
            <b style="font-size:14px">{{ c.title }}</b>
            <button class="linkish" @click.stop="remove(c.id)">删</button>
          </div>
          <div class="muted" style="font-size:12px;margin-top:4px">{{ c.status }}</div>
        </div>
      </div>
      <div class="panel" style="display:flex;flex-direction:column;gap:12px">
        <div style="flex:1;overflow:auto;display:flex;flex-direction:column;gap:10px;min-height:360px">
          <div v-for="(m,i) in messages" :key="i" class="msg" :class="m.role">
            <div class="role">{{ m.role === 'user' ? '我' : '问鉴控' }}</div>
            <div class="bubble">{{ m.content }}</div>
          </div>
          <div v-if="current?.status==='running'" class="muted">正在思考…</div>
          <div v-if="!messages.length" class="empty">开始提问吧，例如「某机构是否适合准入？」</div>
        </div>
        <div class="row-actions">
          <input v-model="input" placeholder="输入风控问题…" style="flex:1;border:1px solid var(--line);border-radius:14px;padding:12px 14px"
                 @keydown.enter.exact.prevent="send" />
          <button class="btn" :disabled="sending || current?.status==='running'" @click="send">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg { max-width: 85%; }
.msg.user { align-self: flex-end; }
.msg.assistant { align-self: flex-start; }
.role { font-size: 12px; color: var(--muted); margin-bottom: 4px; font-weight: 700; }
.bubble { background: #f7f1e8; border-radius: 16px; padding: 12px 14px; white-space: pre-wrap; line-height: 1.55; }
.msg.user .bubble { background: #3d342c; color: #fff; }
.linkish { border:0; background:transparent; color: var(--accent); cursor:pointer; font-weight:700; }
</style>
