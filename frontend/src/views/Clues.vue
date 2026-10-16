<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const rows = ref<any[]>([])
const q = ref('')
const status = ref('')
const level = ref('')
const err = ref('')
const open = ref(false)
const empty = () => ({ partnerName: '', title: '', summary: '', level: 3, partnerType: 'loan' })
const form = ref(empty())

async function load() {
  if (!auth.currentOrgId) return
  try {
    rows.value = await api.listClues({
      q: q.value || undefined,
      status: status.value || undefined,
      level: level.value ? Number(level.value) : undefined,
    })
  } catch (e) { err.value = e instanceof Error ? e.message : String(e) }
}

function openCreate() {
  form.value = empty()
  open.value = true
}

async function create() {
  try {
    await api.createClue(form.value)
    open.value = false
    form.value = empty()
    await load()
  } catch (e) { alert(e instanceof Error ? e.message : String(e)) }
}

async function mark(id: number, next: string) {
  await api.updateClue(id, { status: next, progress: next === '已闭环' ? 100 : 50 })
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>线索台账</h1>
      <div class="toolbar" v-if="auth.currentOrgId">
        <input v-model="q" placeholder="搜索主体 / 标题" @keyup.enter="load" />
        <select v-model="level" @change="load"><option value="">等级</option><option value="1">L1</option><option value="2">L2</option><option value="3">L3</option><option value="4">L4</option></select>
        <select v-model="status" @change="load"><option value="">状态</option><option>待复核</option><option>跟进中</option><option>已闭环</option></select>
        <button class="btn" type="button" @click="openCreate">新建线索</button>
      </div>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <div v-else class="panel" style="padding: 4px 8px 8px">
      <table class="table" v-if="rows.length">
        <thead><tr><th>主体</th><th>标题</th><th>等级</th><th>状态</th><th></th></tr></thead>
        <tbody>
          <tr v-for="c in rows" :key="c.id">
            <td>{{ c.partnerName }}</td>
            <td class="clip" :title="c.summary">{{ c.title }}</td>
            <td><span class="badge" :class="'l'+c.level">L{{ c.level }}</span></td>
            <td>{{ c.status }}</td>
            <td class="row-actions">
              <button class="btn mini secondary" @click="mark(c.id,'跟进中')">跟进</button>
              <button class="btn mini secondary" @click="mark(c.id,'已闭环')">闭环</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">{{ err || '暂无线索' }}</div>
    </div>

    <div v-if="open" class="mask" @click.self="open = false">
      <div class="dialog" role="dialog">
        <h3>新建线索</h3>
        <div class="field"><label>主体名称</label><input v-model="form.partnerName" /></div>
        <div class="field"><label>标题</label><input v-model="form.title" /></div>
        <div class="field"><label>摘要</label><textarea v-model="form.summary" rows="4" /></div>
        <div class="field"><label>等级</label>
          <select v-model.number="form.level"><option :value="1">L1</option><option :value="2">L2</option><option :value="3">L3</option><option :value="4">L4</option></select>
        </div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="open = false">取消</button>
          <button class="btn" type="button" @click="create">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
