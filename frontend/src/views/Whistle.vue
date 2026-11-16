<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api'
import ReportView from '../components/ReportView.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const rows = ref<any[]>([])
const clues = ref<any[]>([])
const partners = ref<any[]>([])
const current = ref<any>(null)
const selected = ref<any>(null)
const kindBusy = ref('')
const onDate = ref(new Date().toISOString().slice(0, 10))
const allPartners = ref(true)
const picked = ref<number[]>([])
const levelFilter = ref<'all' | number>('all')
const schedule = ref({ dailyEnabled: true, dailyTime: '08:00', weeklyEnabled: true, weeklyDow: 1, weeklyTime: '09:00' })
const msg = ref('')
const err = ref('')
const q = ref('')
let timer: number | undefined

const shown = computed(() => {
  const s = q.value.trim().toLowerCase()
  if (!s) return rows.value
  return rows.value.filter((r) => String(r.title || '').toLowerCase().includes(s))
})
const filteredClues = computed(() => {
  if (levelFilter.value === 'all') return clues.value
  return clues.value.filter((c) => c.level === levelFilter.value)
})
const alerts = computed(() => clues.value.filter((c) => c.level <= 2 && String(c.status || '').match(/待|复核/)))

async function load() {
  if (!auth.currentOrgId) return
  rows.value = await api.listWhistleReports()
  partners.value = await api.listPartners()
  clues.value = await api.listClues()
  const saved = await api.getWhistleSchedule()
  schedule.value = {
    dailyEnabled: !!saved.dailyEnabled,
    dailyTime: saved.dailyTime || '08:00',
    weeklyEnabled: !!saved.weeklyEnabled,
    weeklyDow: Number(saved.weeklyDow || 1),
    weeklyTime: saved.weeklyTime || '09:00',
  }
  const ids = Array.isArray(saved.partnerIds) ? saved.partnerIds.map(Number) : []
  allPartners.value = ids.length === 0
  picked.value = ids
  if (current.value?.id) current.value = await api.getWhistleReport(current.value.id)
}

async function saveSchedule() {
  err.value = ''
  msg.value = ''
  try {
    if (!allPartners.value && !picked.value.length) throw new Error('指定机构时至少勾选一家，或改回全部机构')
    await api.saveWhistleSchedule({
      ...schedule.value,
      partnerIds: allPartners.value ? [] : picked.value,
    })
    msg.value = '定时已保存'
  } catch (e) { err.value = e instanceof Error ? e.message : String(e) }
}

async function run(kind: 'daily' | 'weekly') {
  if (!allPartners.value && !picked.value.length) { err.value = '指定机构时至少勾选一家'; return }
  kindBusy.value = kind
  err.value = ''
  try {
    const body: any = { kind, onDate: onDate.value }
    if (!allPartners.value) body.partnerIds = picked.value
    current.value = await api.startWhistleJob(body)
    poll()
  } catch (e) { err.value = e instanceof Error ? e.message : String(e) }
  finally { kindBusy.value = '' }
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
  selected.value = null
  current.value = await api.getWhistleReport(r.id)
  if (current.value.status === 'running') poll()
}

function toggle(id: number) {
  picked.value = picked.value.includes(id) ? picked.value.filter((x) => x !== id) : [...picked.value, id]
}

async function setLevel(level: number) {
  if (!selected.value) return
  selected.value = await api.updateClue(selected.value.id, { level })
  await load()
}

async function closeClue() {
  if (!selected.value) return
  selected.value = await api.updateClue(selected.value.id, { status: '已关闭' })
  await load()
}

onMounted(load)
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div>
    <div class="page-head">
      <h1>风险吹哨</h1>
      <div class="toolbar" v-if="auth.currentOrgId">
        <input v-model="onDate" type="date" />
        <button class="btn secondary" type="button" :disabled="!!kindBusy" @click="run('daily')">{{ kindBusy === 'daily' ? '生成中…' : '出日报' }}</button>
        <button class="btn secondary" type="button" :disabled="!!kindBusy" @click="run('weekly')">{{ kindBusy === 'weekly' ? '生成中…' : '出周报' }}</button>
      </div>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <template v-else>
      <div class="panel" style="margin-bottom: 12px">
        <div class="row-actions" style="justify-content: space-between; margin-bottom: 10px">
          <strong>跑批设置</strong>
          <button class="btn secondary" type="button" @click="saveSchedule">保存定时</button>
        </div>
        <div class="schedule-grid">
          <label class="schedule-row"><input type="checkbox" v-model="schedule.dailyEnabled" /> 日报 <input type="time" v-model="schedule.dailyTime" /></label>
          <label class="schedule-row">
            <input type="checkbox" v-model="schedule.weeklyEnabled" /> 周报
            <select v-model.number="schedule.weeklyDow">
              <option :value="1">周一</option><option :value="2">周二</option><option :value="3">周三</option>
              <option :value="4">周四</option><option :value="5">周五</option><option :value="6">周六</option><option :value="7">周日</option>
            </select>
            <input type="time" v-model="schedule.weeklyTime" />
          </label>
        </div>
        <label class="schedule-row" style="margin-top: 10px; width: fit-content"><input type="checkbox" v-model="allPartners" /> 全部机构</label>
        <div v-if="!allPartners" class="partner-picks">
          <label v-for="p in partners" :key="p.id"><input type="checkbox" :checked="picked.includes(p.id)" @change="toggle(p.id)" /> {{ p.name }}</label>
        </div>
        <p v-if="!partners.length" class="ready-line">请先在机构名单导入合作机构。</p>
        <p v-if="msg" class="drop-note">{{ msg }}</p>
        <p v-if="err" class="drop-err">{{ err }}</p>
      </div>

      <div class="panel" style="padding: 4px 8px 8px; margin-bottom: 12px">
        <div class="row-actions" style="padding: 8px">
          <strong>报告</strong>
          <input v-model="q" placeholder="搜索报告" />
        </div>
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
        <div v-else class="empty">暂无报告。选择机构后点出日报或出周报。</div>
      </div>

      <div class="panel" style="padding: 4px 8px 8px">
        <div class="row-actions" style="padding: 8px">
          <strong>线索</strong>
          <span class="muted">待处置 1/2 级 {{ alerts.length }}</span>
          <button v-for="lv in ['all', 1, 2, 3, 4]" :key="String(lv)" class="btn secondary" type="button" @click="levelFilter = lv === 'all' ? 'all' : Number(lv)">
            {{ lv === 'all' ? '全部' : 'L' + lv }}
          </button>
        </div>
        <table class="table" v-if="filteredClues.length">
          <thead><tr><th>等级</th><th>主体</th><th>标题</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="c in filteredClues" :key="c.id" style="cursor:pointer" @click="selected = c; current = null">
              <td>L{{ c.level }}</td>
              <td>{{ c.partnerName }}</td>
              <td>{{ c.title }}</td>
              <td>{{ c.status }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">暂无线索</div>
      </div>
    </template>

    <div v-if="current" class="mask" @click.self="current = null">
      <div class="dialog wide report-dialog" role="dialog">
        <h3>{{ current.title }}</h3>
        <div class="row-actions" style="margin-bottom:12px">
          <span class="badge" :class="current.status==='done'?'ok':''">{{ current.status }} {{ current.progress }}%</span>
        </div>
        <ReportView v-if="current.markdown" :title="current.title" :markdown="current.markdown" />
        <p v-else>{{ current.summary }}</p>
      </div>
    </div>

    <div v-if="selected" class="mask" @click.self="selected = null">
      <div class="dialog" role="dialog">
        <h3>{{ selected.title }}</h3>
        <p class="ready-line">{{ selected.partnerName }} · L{{ selected.level }} · {{ selected.status }}</p>
        <p>{{ selected.summary }}</p>
        <div class="level-picks">
          <button v-for="lv in [1, 2, 3, 4]" :key="lv" class="btn secondary" type="button" @click="setLevel(lv)">改为 L{{ lv }}</button>
          <button class="btn secondary" type="button" @click="closeClue">人工关闭</button>
        </div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="selected = null">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>
