<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const rows = ref<any[]>([])
const current = ref<any>(null)
const partners = ref<any[]>([])
const partnerId = ref<number | ''>('')
const kind = ref('daily')
const onDate = ref('')
const busy = ref(false)
const composer = ref(false)
const q = ref('')
let timer: number | undefined

async function load() {
  if (!auth.currentOrgId) return
  rows.value = await api.listWhistleReports()
  partners.value = await api.listPartners()
  if (current.value?.id) current.value = await api.getWhistleReport(current.value.id)
}

async function start() {
  busy.value = true
  try {
    const body: any = { kind: kind.value, batchAll: !partnerId.value }
    if (onDate.value) body.onDate = onDate.value
    if (partnerId.value) body.partnerId = partnerId.value
    current.value = await api.startWhistleJob(body)
    composer.value = false
    await load()
    poll()
  } catch (e) { alert(e instanceof Error ? e.message : String(e)) }
  finally { busy.value = false }
}

function poll() {
  window.clearInterval(timer)
  timer = window.setInterval(async () => {
    if (!current.value?.id) return
    current.value = await api.getWhistleReport(current.value.id)
    if (current.value.status === 'done' || current.value.status === 'failed') {
      window.clearInterval(timer)
      await load()
    }
  }, 1500)
}

async function open(r: any) {
  current.value = await api.getWhistleReport(r.id)
  if (current.value.status === 'running') poll()
}

const shown = computed(() => {
  const s = q.value.trim().toLowerCase()
  if (!s) return rows.value
  return rows.value.filter((r) => String(r.title || '').toLowerCase().includes(s))
})

onMounted(load)
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div>
    <div class="page-head">
      <h1>风险吹哨</h1>
      <div class="toolbar" v-if="auth.currentOrgId">
        <input v-model="q" placeholder="搜索报告" />
        <button class="btn" type="button" @click="composer = true">启动吹哨</button>
      </div>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <div v-else class="panel" style="padding: 4px 8px 8px">
      <table class="table" v-if="shown.length">
        <thead><tr><th>报告</th><th>状态</th><th>线索</th><th>L1 / L2</th></tr></thead>
        <tbody>
          <tr v-for="r in shown" :key="r.id" style="cursor:pointer" @click="open(r)">
            <td>{{ r.title }}</td>
            <td>{{ r.status }} {{ r.progress }}%</td>
            <td>{{ r.clueCount }}</td>
            <td>{{ r.level1 }} / {{ r.level2 }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无报告</div>
    </div>

    <div v-if="composer" class="mask" @click.self="composer = false">
      <div class="dialog" role="dialog">
        <h3>启动吹哨</h3>
        <div class="field"><label>周期</label>
          <select v-model="kind"><option value="daily">日报</option><option value="weekly">周报</option></select>
        </div>
        <div class="field"><label>指定日期</label><input v-model="onDate" type="date" /></div>
        <div class="field"><label>机构</label>
          <select v-model="partnerId">
            <option value="">全部机构</option>
            <option v-for="p in partners" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="composer = false">取消</button>
          <button class="btn" type="button" :disabled="busy" @click="start">{{ busy ? '启动中…' : '启动' }}</button>
        </div>
      </div>
    </div>

    <div v-if="current" class="mask" @click.self="current = null">
      <div class="dialog wide" role="dialog">
        <h3>{{ current.title }}</h3>
        <div class="row-actions" style="margin-bottom:12px">
          <span class="badge" :class="current.status==='done'?'ok':''">{{ current.status }} {{ current.progress }}%</span>
        </div>
        <p>{{ current.summary }}</p>
        <div class="mono">{{ current.markdown }}</div>
      </div>
    </div>
  </div>
</template>
