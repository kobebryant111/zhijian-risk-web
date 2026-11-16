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
const tab = ref<'reports' | 'clues'>('reports')
const levels = ['all', 1, 2, 3, 4] as const
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

function statusClass(s: string) {
  if (s === 'done' || s === '已关闭' || s === '已闭环') return 'ok'
  if (s === 'failed' || s === '待复核') return 'danger'
  return ''
}

function pickLevel(lv: 'all' | number) {
  levelFilter.value = lv
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
        <input v-model="onDate" class="date-field" type="date" />
        <button class="btn secondary" type="button" :disabled="!!kindBusy" @click="run('daily')">{{ kindBusy === 'daily' ? '生成中…' : '出日报' }}</button>
        <button class="btn secondary" type="button" :disabled="!!kindBusy" @click="run('weekly')">{{ kindBusy === 'weekly' ? '生成中…' : '出周报' }}</button>
      </div>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <template v-else>
      <div class="panel setup">
        <div class="block-head">
          <div>
            <h2>跑批设置</h2>
            <p>到点按所选范围自动出报，改完要点保存定时。</p>
          </div>
          <div class="block-actions">
            <span v-if="msg" class="drop-note">{{ msg }}</span>
            <span v-if="err" class="drop-err">{{ err }}</span>
            <button class="btn secondary" type="button" @click="saveSchedule">保存定时</button>
          </div>
        </div>
        <div class="schedule-grid">
          <div class="sched-card" :class="{ on: schedule.dailyEnabled }">
            <label class="sched-top"><input type="checkbox" v-model="schedule.dailyEnabled" /> 日报</label>
            <label class="sched-field"><span>每天</span><input type="time" v-model="schedule.dailyTime" /></label>
          </div>
          <div class="sched-card" :class="{ on: schedule.weeklyEnabled }">
            <label class="sched-top"><input type="checkbox" v-model="schedule.weeklyEnabled" /> 周报</label>
            <div class="sched-fields">
              <select v-model.number="schedule.weeklyDow">
                <option :value="1">周一</option><option :value="2">周二</option><option :value="3">周三</option>
                <option :value="4">周四</option><option :value="5">周五</option><option :value="6">周六</option><option :value="7">周日</option>
              </select>
              <input type="time" v-model="schedule.weeklyTime" />
            </div>
          </div>
        </div>
        <div class="scope-row">
          <span class="scope-label">范围</span>
          <div class="seg">
            <button type="button" :class="{ on: allPartners }" @click="allPartners = true">全部机构</button>
            <button type="button" :class="{ on: !allPartners }" :disabled="!partners.length" @click="allPartners = false">指定机构</button>
          </div>
          <span v-if="!partners.length" class="muted">请先在机构名单导入合作机构</span>
          <span v-else-if="!allPartners" class="muted">已选 {{ picked.length }} 家</span>
        </div>
        <div v-if="!allPartners && partners.length" class="partner-picks">
          <label v-for="p in partners" :key="p.id" :class="{ on: picked.includes(p.id) }">
            <input type="checkbox" :checked="picked.includes(p.id)" @change="toggle(p.id)" /> {{ p.name }}
          </label>
        </div>
      </div>

      <div class="panel board">
        <div class="board-head">
          <div class="tabs">
            <button type="button" :class="{ on: tab === 'reports' }" @click="tab = 'reports'">报告</button>
            <button type="button" :class="{ on: tab === 'clues' }" @click="tab = 'clues'">线索</button>
          </div>
          <div class="board-tools">
            <input v-if="tab === 'reports'" v-model="q" class="search" placeholder="搜索报告" />
            <template v-else>
              <span class="badge" :class="alerts.length ? 'danger' : ''">待处置 {{ alerts.length }}</span>
              <div class="seg">
                <button v-for="lv in levels" :key="String(lv)" type="button" :class="{ on: levelFilter === lv }" @click="pickLevel(lv)">
                  {{ lv === 'all' ? '全部' : 'L' + lv }}
                </button>
              </div>
            </template>
          </div>
        </div>
        <div v-show="tab === 'reports'" class="board-body">
          <table class="table" v-if="shown.length">
            <thead><tr><th>报告</th><th>状态</th><th>线索</th><th>L1 / L2</th></tr></thead>
            <tbody>
              <tr v-for="r in shown" :key="r.id" class="click" @click="open(r)">
                <td>{{ r.title }}</td>
                <td><span class="badge" :class="statusClass(r.status)">{{ r.status }}</span> <span class="muted">{{ r.progress }}%</span></td>
                <td>{{ r.clueCount }}</td>
                <td>{{ r.level1 }} / {{ r.level2 }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">暂无报告。选好日期后点出日报或出周报。</div>
        </div>
        <div v-show="tab === 'clues'" class="board-body">
          <table class="table" v-if="filteredClues.length">
            <thead><tr><th>等级</th><th>主体</th><th>标题</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="c in filteredClues" :key="c.id" class="click" @click="selected = c; current = null">
                <td><span class="badge" :class="'l' + c.level">L{{ c.level }}</span></td>
                <td>{{ c.partnerName }}</td>
                <td>{{ c.title }}</td>
                <td><span class="badge" :class="statusClass(c.status)">{{ c.status }}</span></td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">暂无线索</div>
        </div>
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
        <div class="clue-actions">
          <div class="seg">
            <button v-for="lv in [1, 2, 3, 4]" :key="lv" type="button" :class="{ on: selected.level === lv }" @click="setLevel(lv)">L{{ lv }}</button>
          </div>
          <button class="btn secondary" type="button" @click="closeClue">人工关闭</button>
        </div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="selected = null">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>
