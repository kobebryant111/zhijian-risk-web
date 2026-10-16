<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const rows = ref<any[]>([])
const current = ref<any>(null)
const form = ref({ partnerName: '', partnerType: 'loan', scenario: '准入', materialsText: '' })
const busy = ref(false)
const composer = ref(false)
const q = ref('')
let timer: number | undefined

async function load() {
  if (!auth.currentOrgId) return
  rows.value = await api.listAdmission()
  if (current.value?.id) current.value = await api.getAdmission(current.value.id)
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
      <div class="dialog" role="dialog">
        <h3>新建评审</h3>
        <div class="field"><label>主体名称</label><input v-model="form.partnerName" placeholder="拟准入机构全称" /></div>
        <div class="field"><label>类型</label>
          <select v-model="form.partnerType">
            <option value="loan">助贷</option>
            <option value="guarantee">融资担保</option>
            <option value="collection">催收</option>
            <option value="payment">支付</option>
            <option value="data">数据</option>
            <option value="traffic">引流</option>
          </select>
        </div>
        <div class="field"><label>场景</label>
          <select v-model="form.scenario"><option>准入</option><option>续签</option><option>扩围</option></select>
        </div>
        <div class="field"><label>尽调材料</label><textarea v-model="form.materialsText" rows="8" placeholder="可粘贴尽调说明、财报或处罚文书"></textarea></div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="composer = false">取消</button>
          <button class="btn" type="button" :disabled="busy" @click="start">{{ busy ? '提交中…' : '发起' }}</button>
        </div>
      </div>
    </div>

    <div v-if="current && !composer" class="mask" @click.self="current = null">
      <div class="dialog wide" role="dialog">
        <h3>{{ current.partnerName }}</h3>
        <div class="row-actions" style="margin-bottom:10px">
          <span class="badge">{{ current.conclusion }}</span>
          <span class="badge" :class="current.status==='done'?'ok':''">{{ current.status }} {{ current.progress }}%</span>
        </div>
        <p>{{ current.summary }}</p>
        <div class="mono">{{ current.markdown }}</div>
      </div>
    </div>
  </div>
</template>
