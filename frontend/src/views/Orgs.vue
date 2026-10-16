<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, type OrgDetail } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const list = ref<OrgDetail[]>([])
const err = ref('')
const msg = ref('')
const showCreate = ref(false)
const form = reactive({ code: '', name: '', description: '' })

async function load() {
  err.value = ''
  try {
    list.value = await api.listOrgs()
    await auth.refreshMe()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

async function create() {
  err.value = ''
  msg.value = ''
  try {
    const o = await api.createOrg({ ...form })
    showCreate.value = false
    form.code = ''
    form.name = ''
    form.description = ''
    msg.value = `已创建「${o.name}」`
    await auth.refreshMe()
    await auth.switchOrg(o.id)
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

async function enter(o: OrgDetail) {
  try {
    await auth.switchOrg(o.id)
    router.push(`/app/orgs/${o.id}`)
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>组织</h1>
      <button v-if="auth.platformAdmin" class="btn" type="button" @click="showCreate = !showCreate">
        {{ showCreate ? '取消' : '创建组织' }}
      </button>
    </div>

    <div v-if="err" class="toast bad">{{ err }}</div>
    <div v-if="msg" class="toast ok">{{ msg }}</div>

    <div v-if="showCreate" class="mask" @click.self="showCreate = false">
      <div class="dialog" role="dialog">
        <h3>新建组织</h3>
        <div class="field"><label>编码</label><input v-model="form.code" placeholder="risk-coop" /></div>
        <div class="field"><label>名称</label><input v-model="form.name" placeholder="风险管理部" /></div>
        <div class="field"><label>说明</label><input v-model="form.description" placeholder="可选" /></div>
        <div class="dialog-actions">
          <button class="btn secondary" type="button" @click="showCreate = false">取消</button>
          <button class="btn" type="button" @click="create">创建</button>
        </div>
      </div>
    </div>

    <div class="cards">
      <article v-for="o in list" :key="o.id" class="panel org" @click="enter(o)">
        <div class="tag">{{ o.code }}</div>
        <h3>{{ o.name }}</h3>
        <div class="foot">
          <span>{{ o.memberCount }} 名成员</span>
          <span class="go">进入</span>
        </div>
      </article>
      <div v-if="list.length === 0" class="panel empty">{{ auth.platformAdmin ? '暂无组织' : '暂无组织，请联系系统管理员创建并分配' }}</div>
    </div>
  </div>
</template>

<style scoped>
.form { margin-bottom: 16px; }
.form h3 { margin: 0 0 14px; font-size: 15px; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 8px; }
.grid .full { grid-column: 1 / -1; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
.org { cursor: pointer; transition: border-color .15s ease, box-shadow .15s ease; }
.org:hover { border-color: #cfe0ff; box-shadow: 0 8px 24px rgba(59, 130, 246, 0.08); }
.tag {
  display: inline-block;
  background: #f1f4f9;
  color: var(--muted);
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 650;
}
.org h3 { margin: 12px 0 18px; font-size: 17px; }
.foot {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--muted);
}
.go { color: var(--accent); font-weight: 650; }
.toast {
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  font-size: 13px;
}
.toast.bad { background: #fee2e2; color: var(--danger); }
.toast.ok { background: #dcfce7; color: var(--ok); }
@media (max-width: 720px) { .grid { grid-template-columns: 1fr; } }
</style>
