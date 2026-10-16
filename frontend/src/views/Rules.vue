<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const rows = ref<any[]>([])
const q = ref('')
const open = ref(false)
const empty = () => ({ ruleCode: '', riskPoint: '', partnerType: 'common', level: 3, triggerText: '', dataSource: '', legalBasis: '' })
const form = ref(empty())

const typeLabel: Record<string, string> = {
  common: '通用',
  loan: '助贷',
  guarantee: '融担',
  ops: '运营',
}

const shown = computed(() => {
  const s = q.value.trim().toLowerCase()
  if (!s) return rows.value
  return rows.value.filter((r) =>
    [r.ruleCode, r.riskPoint, r.triggerText, typeLabel[r.partnerType] || r.partnerType]
      .some((x) => String(x || '').toLowerCase().includes(s)),
  )
})

async function load() {
  if (!auth.currentOrgId) return
  rows.value = await api.listRules()
}

function openCreate() {
  form.value = empty()
  open.value = true
}

async function create() {
  try {
    await api.createRule(form.value)
    open.value = false
    form.value = empty()
    await load()
  } catch (e) { alert(e instanceof Error ? e.message : String(e)) }
}

async function toggle(r: any) {
  await api.updateRule(r.id, { enabled: !r.enabled })
  await load()
}

async function remove(id: number) {
  if (!confirm('确认删除该规则？')) return
  await api.deleteRule(id)
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>规则库</h1>
      <div class="toolbar" v-if="auth.currentOrgId">
        <input v-model="q" placeholder="搜索编码 / 风险点" />
        <button class="btn" type="button" @click="openCreate">新建规则</button>
      </div>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <div v-else class="panel" style="padding: 4px 8px 8px">
      <table class="table" v-if="shown.length">
        <thead><tr><th>编码</th><th>风险点</th><th>类型</th><th>等级</th><th>状态</th><th></th></tr></thead>
        <tbody>
          <tr v-for="r in shown" :key="r.id">
            <td>{{ r.ruleCode }}</td>
            <td class="clip" :title="r.triggerText">{{ r.riskPoint }}</td>
            <td>{{ typeLabel[r.partnerType] || r.partnerType }}</td>
            <td><span class="badge" :class="'l'+r.level">L{{ r.level }}</span></td>
            <td>{{ r.enabled ? '启用' : '停用' }}</td>
            <td class="row-actions">
              <button class="btn mini secondary" @click="toggle(r)">{{ r.enabled ? '停用' : '启用' }}</button>
              <button class="btn mini secondary" @click="remove(r.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">{{ q ? '没有匹配的规则' : '暂无规则' }}</div>
    </div>

    <div v-if="open" class="mask" @click.self="open = false">
      <div class="dialog" role="dialog">
        <h3>新建规则</h3>
        <div class="field"><label>编码</label><input v-model="form.ruleCode" placeholder="可选，自动生成" /></div>
        <div class="field"><label>风险点</label><input v-model="form.riskPoint" /></div>
        <div class="field"><label>适用类型</label>
          <select v-model="form.partnerType"><option value="common">通用</option><option value="loan">助贷</option><option value="guarantee">融担</option><option value="ops">运营</option></select>
        </div>
        <div class="field"><label>等级</label>
          <select v-model.number="form.level"><option :value="1">L1</option><option :value="2">L2</option><option :value="3">L3</option><option :value="4">L4</option></select>
        </div>
        <div class="field"><label>触发说明</label><input v-model="form.triggerText" /></div>
        <div class="field"><label>数据来源</label><input v-model="form.dataSource" /></div>
        <div class="field"><label>法规依据</label><input v-model="form.legalBasis" /></div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="open = false">取消</button>
          <button class="btn" type="button" @click="create">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
