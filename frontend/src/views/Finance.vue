<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const rows = ref<any[]>([])
const partners = ref<any[]>([])
const current = ref<any>(null)
const partnerId = ref<number | ''>('')
const partnerName = ref('')
const period = ref('')
const documentText = ref('')
const busy = ref(false)
const composer = ref(false)
const q = ref('')
let timer: number | undefined

async function load() {
  if (!auth.currentOrgId) return
  rows.value = await api.listFinance()
  partners.value = await api.listPartners()
  if (current.value?.id) current.value = await api.getFinance(current.value.id)
}

async function start() {
  busy.value = true
  try {
    const body: any = {}
    if (partnerId.value) body.partnerId = partnerId.value
    else body.partnerName = partnerName.value
    if (period.value) body.period = period.value
    body.documentText = documentText.value
    current.value = await api.startFinance(body)
    composer.value = false
    await load(); poll()
  } catch (e) { alert(e instanceof Error ? e.message : String(e)) }
  finally { busy.value = false }
}

function poll() {
  window.clearInterval(timer)
  timer = window.setInterval(async () => {
    if (!current.value?.id) return
    current.value = await api.getFinance(current.value.id)
    if (current.value.status === 'done' || current.value.status === 'failed') {
      window.clearInterval(timer); await load()
    }
  }, 1500)
}

async function open(r: any) {
  current.value = await api.getFinance(r.id)
  if (current.value.status === 'running') poll()
}

const shown = computed(() => {
  const s = q.value.trim().toLowerCase()
  if (!s) return rows.value
  return rows.value.filter((r) => String(r.partnerName || '').toLowerCase().includes(s))
})

onMounted(load)
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div>
    <div class="page-head">
      <h1>经营守护</h1>
      <div class="toolbar" v-if="auth.currentOrgId">
        <input v-model="q" placeholder="搜索主体" />
        <button class="btn" type="button" @click="composer = true">新建报告</button>
      </div>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <div v-else class="panel" style="padding: 4px 8px 8px">
      <table class="table" v-if="shown.length">
        <thead><tr><th>主体</th><th>评级</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="r in shown" :key="r.id" style="cursor:pointer" @click="open(r)">
            <td>{{ r.partnerName }}</td>
            <td>{{ r.grade }}</td>
            <td>{{ r.status }} {{ r.progress }}%</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无报告</div>
    </div>

    <div v-if="composer" class="mask" @click.self="composer = false">
      <div class="dialog" role="dialog">
        <h3>新建经营报告</h3>
        <div class="field"><label>机构</label>
          <select v-model="partnerId">
            <option value="">手动输入主体</option>
            <option v-for="p in partners" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <div class="field" v-if="!partnerId"><label>主体名称</label><input v-model="partnerName" /></div>
        <div class="field"><label>报告期间</label><input v-model="period" placeholder="如 2025全年，可留空由正文识别" /></div>
        <div class="field"><label>财报正文</label><textarea v-model="documentText" rows="8" placeholder="粘贴审计报告、年报或季报正文"></textarea></div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="composer = false">取消</button>
          <button class="btn" type="button" :disabled="busy" @click="start">{{ busy ? '生成中…' : '生成' }}</button>
        </div>
      </div>
    </div>

    <div v-if="current && !composer" class="mask" @click.self="current = null">
      <div class="dialog wide" role="dialog">
        <h3>{{ current.partnerName }}</h3>
        <div class="row-actions" style="margin-bottom:10px">
          <span class="badge">评级 {{ current.grade }}</span>
          <span class="badge">{{ current.status }}</span>
        </div>
        <p>{{ current.summary }}</p>
        <div class="mono">{{ current.markdown }}</div>
      </div>
    </div>
  </div>
</template>
