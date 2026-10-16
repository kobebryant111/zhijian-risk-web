<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const data = ref<any>(null)
const err = ref('')
const loading = ref(false)
const tab = ref<'clues' | 'reports' | 'trend'>('clues')

const stats = computed(() => {
  const d = data.value
  if (!d) return []
  return [
    { key: 'health', label: '健康分', value: d.healthScore ?? '-', unit: '分', to: '/app/clues', tip: d.healthLevel || '' },
    { key: 'partners', label: '合作机构', value: d.partnerCount ?? 0, unit: '家', to: '/app/partners', tip: '' },
    { key: 'clues', label: '线索总数', value: d.clueStats?.total ?? 0, unit: '条', to: '/app/clues', tip: '' },
    { key: 'pending', label: '待复核', value: d.clueStats?.pending ?? 0, unit: '条', to: '/app/clues', tip: '' },
  ]
})

const shortcuts = [
  { to: '/app/whistle', title: '风险吹哨', desc: '日/月监测' },
  { to: '/app/admission', title: '准入瞭望', desc: '尽调画像' },
  { to: '/app/finance', title: '经营守护', desc: '经营分析' },
  { to: '/app/agent', title: '问鉴控', desc: '智能问答' },
]

async function load() {
  if (!auth.currentOrgId) { data.value = null; return }
  loading.value = true; err.value = ''
  try { data.value = await api.overview() }
  catch (e) { err.value = e instanceof Error ? e.message : String(e) }
  finally { loading.value = false }
}

function go(path: string) { router.push(path) }

function statusClass(s: string) {
  if (s === '已闭环') return 'ok'
  if (s === '待复核') return 'danger'
  return ''
}

onMounted(load)
</script>

<template>
  <div class="wb">
    <div class="page-head">
      <h1>总览</h1>
      <button class="btn secondary" :disabled="loading || !auth.currentOrgId" @click="load">刷新</button>
    </div>

    <div v-if="!auth.currentOrgId" class="panel empty">
      <template v-if="auth.orgs.length">请先在顶部选择组织，名单和报告按组织隔离共享。</template>
      <template v-else>还没有组织。先<router-link class="go-org" to="/app/orgs">创建组织</router-link>，成员才能共享同一套数据。</template>
    </div>
    <div v-else-if="err" class="panel empty">{{ err }}</div>

    <template v-else-if="data">
      <div class="metrics">
        <div
          v-for="s in stats"
          :key="s.key"
          class="metric"
          role="button"
          tabindex="0"
          @click="go(s.to)"
          @keyup.enter="go(s.to)"
        >
          <div class="metric-top">
            <span>{{ s.label }}</span>
            <span class="linkish">查看</span>
          </div>
          <div class="metric-val">
            <b>{{ s.value }}</b>
            <small>{{ s.unit }}</small>
          </div>
          <div class="metric-sub" v-if="s.tip">{{ s.tip }}</div>
        </div>
      </div>

      <div class="section">
        <div class="section-head">
          <h2>快捷入口</h2>
        </div>
        <div class="shortcuts">
          <button
            v-for="s in shortcuts"
            :key="s.to"
            type="button"
            class="sc"
            @click="go(s.to)"
          >
            <div class="sc-icon">{{ s.title.slice(0, 1) }}</div>
            <div>
              <b>{{ s.title }}</b>
              <small>{{ s.desc }}</small>
            </div>
          </button>
        </div>
      </div>

      <div class="panel board">
        <div class="board-head">
          <div class="tabs">
            <button type="button" :class="{ on: tab === 'clues' }" @click="tab = 'clues'">最新线索</button>
            <button type="button" :class="{ on: tab === 'reports' }" @click="tab = 'reports'">近期报告</button>
            <button type="button" :class="{ on: tab === 'trend' }" @click="tab = 'trend'">近 14 日</button>
          </div>
          <div class="board-actions">
            <button class="btn" @click="go('/app/whistle')">启动吹哨</button>
          </div>
        </div>

        <div v-show="tab === 'clues'" class="board-body">
          <div v-if="data.recentClues?.length" class="rows">
            <div v-for="c in data.recentClues" :key="c.id" class="row" @click="go('/app/clues')">
              <div class="row-main">
                <b>{{ c.title }}</b>
                <div class="meta">
                  <span>{{ c.partnerName || '未命名主体' }}</span>
                  <span class="dot">·</span>
                  <span class="badge" :class="'l' + c.level">L{{ c.level }}</span>
                  <span class="dot">·</span>
                  <span class="badge" :class="statusClass(c.status)">{{ c.status }}</span>
                </div>
              </div>
              <div class="row-side muted">{{ String(c.createdAt || '').slice(0, 16).replace('T', ' ') }}</div>
            </div>
          </div>
          <div v-else class="empty">暂无线索</div>
        </div>

        <div v-show="tab === 'reports'" class="board-body">
          <div v-if="data.recentReports?.length" class="rows">
            <div v-for="r in data.recentReports" :key="r.id" class="row" @click="go('/app/whistle')">
              <div class="row-main">
                <b>{{ r.title }}</b>
                <div class="meta muted">{{ r.summary || r.status }}</div>
              </div>
              <div class="row-side muted">{{ String(r.createdAt || '').slice(0, 16).replace('T', ' ') }}</div>
            </div>
          </div>
          <div v-else class="empty">暂无报告</div>
        </div>

        <div v-show="tab === 'trend'" class="board-body">
          <div class="trend">
            <div v-for="p in data.trend" :key="p.date" class="bar-wrap" :title="`${p.date}: ${p.count}`">
              <div class="bar" :style="{ height: Math.max(4, (p.count || 0) * 16) + 'px' }"></div>
              <small>{{ String(p.date).slice(5) }}</small>
            </div>
          </div>
          <div v-if="data.typeCards?.length" class="types">
            <div v-for="c in data.typeCards" :key="c.type" class="type">
              <span>{{ c.label }}</span>
              <b>{{ c.count }}</b>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.go-org { color: var(--accent); font-weight: 650; }
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.metric {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 16px 18px;
  box-shadow: none;
  cursor: pointer;
  transition: border-color .15s ease;
}
.metric:hover { border-color: #c5d8f7; }
.metric-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}
.linkish { color: var(--accent); font-size: 12px; font-weight: 600; }
.metric-val {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 12px;
}
.metric-val b {
  font-size: 30px;
  font-weight: 700;
  color: var(--accent);
  letter-spacing: -0.03em;
  line-height: 1;
}
.metric-val small { color: var(--muted); font-size: 13px; }
.metric-sub {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}

.section { margin-bottom: 18px; }
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.section-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}
.shortcuts {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.sc {
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  cursor: pointer;
  box-shadow: var(--shadow);
}
.sc:hover { border-color: #cfe0ff; }
.sc b { display: block; font-size: 14px; }
.sc small { color: var(--muted); font-size: 12px; }
.sc-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--accent-soft);
  color: var(--accent-deep);
  display: grid;
  place-items: center;
  font-weight: 700;
  flex-shrink: 0;
}

.board { padding: 0; overflow: hidden; }
.board-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
}
.board-head .tabs { border-bottom: 0; margin: 0; }
.board-actions { display: flex; gap: 8px; }
.board-body { padding: 8px 8px 12px; min-height: 220px; }

.rows { display: flex; flex-direction: column; }
.row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 12px;
  border-radius: 10px;
  cursor: pointer;
}
.row:hover { background: #f7f9fc; }
.row-main b { font-size: 14px; font-weight: 650; }
.meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
  flex-wrap: wrap;
}
.dot { opacity: 0.5; }
.row-side { font-size: 12px; white-space: nowrap; padding-top: 2px; }

.trend {
  display: flex;
  align-items: end;
  gap: 6px;
  height: 140px;
  padding: 16px 12px 8px;
}
.bar-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.bar {
  width: 100%;
  max-width: 22px;
  background: linear-gradient(180deg, #60a5fa, #3b82f6);
  border-radius: 6px 6px 3px 3px;
}
.bar-wrap small { color: var(--muted); font-size: 10px; }
.types {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 4px 12px 12px;
}
.type {
  background: #f7f9fc;
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--muted);
}
.type b { color: var(--ink); font-size: 18px; }

@media (max-width: 1100px) {
  .metrics, .shortcuts, .types { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 720px) {
  .metrics, .shortcuts, .types { grid-template-columns: 1fr; }
  .board-head { flex-direction: column; align-items: stretch; padding-bottom: 12px; }
}
</style>
