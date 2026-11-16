<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api'
import AttachFiles from '../components/AttachFiles.vue'
import ReportView from '../components/ReportView.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const rows = ref<any[]>([])
const current = ref<any>(null)
const form = ref({ partnerName: '', partnerType: 'loan', scenario: '准入', materialsText: '' })
const busy = ref(false)
const composer = ref(false)
const q = ref('')
const ready = ref({ web: false, mcp: false })

function parsed(raw: unknown) {
  if (Array.isArray(raw)) return raw
  if (typeof raw !== 'string' || !raw.trim()) return []
  try { const value = JSON.parse(raw); return Array.isArray(value) ? value : [] } catch { return [] }
}
let timer: number | undefined

function guessType(name: string) {
  if (/担保|融担|再担保/.test(name)) return 'guarantee'
  if (/催收|清收|委外/.test(name)) return 'collection'
  if (/支付|清算/.test(name)) return 'payment'
  if (/数据|征信|评分/.test(name)) return 'data'
  if (/科技|流量|营销|导流/.test(name)) return 'traffic'
  if (/同业|金市|银行间/.test(name)) return 'interbank'
  if (/运营|客服|电销|辅助/.test(name)) return 'ops'
  return 'loan'
}

function onName() {
  if (form.value.partnerName.trim()) form.value.partnerType = guessType(form.value.partnerName)
}

async function load() {
  if (!auth.currentOrgId) return
  rows.value = await api.listAdmission()
  if (current.value?.id) current.value = await api.getAdmission(current.value.id)
  try {
    const view = await api.getSettings()
    ready.value = {
      web: !!view?.baidu?.ready,
      mcp: !!(view?.mcp?.qccReady || view?.mcp?.tycReady),
    }
  } catch { /* 未登录或设置读取失败时仍可发起，评审按未配置处理 */ }
}

async function start() {
  busy.value = true
  try {
    current.value = await api.startAdmission(form.value)
    composer.value = false
    await load(); poll()
  } catch (e) { alert(e instanceof Error ? e.message : String(e)) }
  finally { busy.value = false }
}

function poll() {
  window.clearInterval(timer)
  timer = window.setInterval(async () => {
    if (!current.value?.id) return
    current.value = await api.getAdmission(current.value.id)
    if (current.value.status === 'done' || current.value.status === 'failed') {
      window.clearInterval(timer); await load()
    }
  }, 1500)
}

async function open(r: any) {
  current.value = await api.getAdmission(r.id)
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
      <h1>准入瞭望</h1>
      <div class="toolbar" v-if="auth.currentOrgId">
        <input v-model="q" placeholder="搜索主体" />
        <button class="btn" type="button" @click="composer = true">新建评审</button>
      </div>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <div v-else class="panel" style="padding: 4px 8px 8px">
      <table class="table" v-if="shown.length">
        <thead><tr><th>主体</th><th>结论</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="r in shown" :key="r.id" style="cursor:pointer" @click="open(r)">
            <td>{{ r.partnerName }}</td>
            <td>{{ r.conclusion }}</td>
            <td>{{ r.status }} {{ r.progress }}%</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无评审</div>
    </div>

    <div v-if="composer" class="mask" @click.self="composer = false">
      <div class="dialog wide" role="dialog">
        <h3>新建评审</h3>
        <div class="field"><label>主体名称</label><input v-model="form.partnerName" placeholder="拟准入机构全称" @input="onName" /></div>
        <div class="field"><label>类型</label>
          <select v-model="form.partnerType">
            <option value="loan">助贷</option>
            <option value="guarantee">融资担保</option>
            <option value="collection">催收</option>
            <option value="payment">支付</option>
            <option value="data">数据</option>
            <option value="traffic">引流</option>
            <option value="interbank">金市同业</option>
            <option value="ops">运营辅助</option>
          </select>
        </div>
        <div class="field"><label>场景</label>
          <select v-model="form.scenario"><option>准入</option><option>续签</option><option>扩围</option></select>
        </div>
        <AttachFiles
          v-model="form.materialsText"
          multiple
          heading="财报 / 尽调附件（可选）"
          hint="拖拽到此处或点击选择，可多选。支持 .docx / .xlsx / .xls / .csv / .txt / .md / .json。暂不支持 PDF 和旧版 .doc。"
        />
        <p class="ready-line">
          全网搜 {{ ready.web ? '已就绪' : '未配置' }} · 企查查/天眼查 {{ ready.mcp ? '已就绪' : '未配置' }}
        </p>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="composer = false">取消</button>
          <button class="btn" type="button" :disabled="busy" @click="start">{{ busy ? '分析中…' : '开始准入分析' }}</button>
        </div>
      </div>
    </div>

    <div v-if="current && !composer" class="mask" @click.self="current = null">
      <div class="dialog wide report-dialog" role="dialog">
        <h3>{{ current.partnerName }}</h3>
        <div class="row-actions" style="margin-bottom:10px">
          <span class="badge">{{ current.conclusion }}</span>
          <span class="badge" :class="current.status==='done'?'ok':''">{{ current.status }} {{ current.progress }}%</span>
        </div>
        <p v-if="current.evidenceSources" class="ready-line">证据来源：{{ current.evidenceSources }}</p>
        <table v-if="parsed(current.itemsJson).length" class="mini-table">
          <thead><tr><th>编号</th><th>检查项</th><th>触发</th><th>证据</th></tr></thead>
          <tbody>
            <tr v-for="item in parsed(current.itemsJson)" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.item }}</td>
              <td>{{ item.triggered ? '是' : '否' }}</td>
              <td>{{ item.detail }}</td>
            </tr>
          </tbody>
        </table>
        <ul v-if="parsed(current.remediationJson).length">
          <li v-for="(line, i) in parsed(current.remediationJson)" :key="i">{{ line }}</li>
        </ul>
        <ReportView v-if="current.markdown" :title="current.partnerName" :markdown="current.markdown" />
        <p v-else>{{ current.summary }}</p>
      </div>
    </div>
  </div>
</template>
