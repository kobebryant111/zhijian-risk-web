<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PartnerView } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const list = ref<PartnerView[]>([])
const err = ref('')
const q = ref('')
const open = ref(false)
const editing = ref<PartnerView | null>(null)
const form = reactive({
  name: '',
  alias: '',
  partnerType: 'loan',
  status: 'active',
  uscc: '',
})

const typeLabel: Record<string, string> = {
  loan: '助贷',
  guarantee: '融资担保',
  traffic: '引流',
  payment: '支付',
  data: '数据',
  collection: '催收',
  interbank: '金市同业',
  ops: '运营辅助',
}

const statusLabel: Record<string, string> = {
  active: '合作中',
  paused: '暂停',
  exit: '退出',
}

const shown = computed(() => {
  const s = q.value.trim().toLowerCase()
  if (!s) return list.value
  return list.value.filter((p) =>
    [p.name, p.alias, p.uscc, typeLabel[p.partnerType], statusLabel[p.status]]
      .some((x) => String(x || '').toLowerCase().includes(s)),
  )
})

async function load() {
  err.value = ''
  if (!auth.currentOrgId) {
    list.value = []
    return
  }
  try {
    list.value = await api.listPartners()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', alias: '', partnerType: 'loan', status: 'active', uscc: '' })
  open.value = true
}

function openEdit(p: PartnerView) {
  editing.value = p
  Object.assign(form, {
    name: p.name,
    alias: p.alias,
    partnerType: p.partnerType,
    status: p.status,
    uscc: p.uscc,
  })
  open.value = true
}

async function save() {
  err.value = ''
  try {
    if (editing.value) {
      await api.updatePartner(editing.value.id, { ...form })
    } else {
      await api.createPartner({ ...form })
    }
    open.value = false
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

async function remove(p: PartnerView) {
  if (!confirm(`删除「${p.name}」？`)) return
  try {
    await api.deletePartner(p.id)
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>机构名单</h1>
      <div class="toolbar" v-if="auth.currentOrgId">
        <input v-model="q" placeholder="搜索名称 / 简称" />
        <button class="btn" type="button" @click="openCreate">新建机构</button>
      </div>
    </div>

    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <div v-else-if="err" class="panel empty">{{ err }}</div>
    <div v-else class="panel" style="padding: 4px 8px 8px">
      <table class="table" v-if="shown.length">
        <thead><tr><th>名称</th><th>简称</th><th>类型</th><th>状态</th><th></th></tr></thead>
        <tbody>
          <tr v-for="p in shown" :key="p.id">
            <td>{{ p.name }}</td>
            <td>{{ p.alias || '—' }}</td>
            <td>{{ typeLabel[p.partnerType] || p.partnerType }}</td>
            <td>{{ statusLabel[p.status] || p.status }}</td>
            <td class="row-actions">
              <button class="btn mini secondary" type="button" @click="openEdit(p)">编辑</button>
              <button class="btn mini secondary" type="button" @click="remove(p)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">{{ q ? '没有匹配的机构' : '暂无机构' }}</div>
    </div>

    <div v-if="open" class="mask" @click.self="open = false">
      <div class="dialog" role="dialog">
        <h3>{{ editing ? '编辑机构' : '新建机构' }}</h3>
        <div v-if="err" class="empty" style="padding:0 0 10px;text-align:left;color:var(--danger)">{{ err }}</div>
        <div class="field"><label>全称</label><input v-model="form.name" /></div>
        <div class="field"><label>简称</label><input v-model="form.alias" /></div>
        <div class="field">
          <label>类型</label>
          <select v-model="form.partnerType">
            <option v-for="(lab, key) in typeLabel" :key="key" :value="key">{{ lab }}</option>
          </select>
        </div>
        <div class="field">
          <label>状态</label>
          <select v-model="form.status">
            <option value="active">合作中</option>
            <option value="paused">暂停</option>
            <option value="exit">退出</option>
          </select>
        </div>
        <div class="field"><label>统一社会信用代码</label><input v-model="form.uscc" /></div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="open = false">取消</button>
          <button class="btn" type="button" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
