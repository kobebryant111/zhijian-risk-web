<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type MemberView, type UserBrief } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const orgId = computed(() => Number(route.params.id))
const members = ref<MemberView[]>([])
const users = ref<UserBrief[]>([])
const q = ref('')
const err = ref('')
const msg = ref('')
const form = reactive({ userId: 0, role: 'ORG_OPERATOR' })

const canManage = computed(() => auth.platformAdmin)

async function load() {
  err.value = ''
  try {
    if (auth.currentOrgId !== orgId.value) {
      await auth.switchOrg(orgId.value)
    }
    members.value = await api.members(orgId.value)
    if (canManage.value) users.value = await api.searchUsers(q.value)
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

async function add() {
  if (!form.userId) return
  err.value = ''
  msg.value = ''
  try {
    await api.addMember(orgId.value, { userId: form.userId, role: form.role })
    msg.value = '已拉入组织'
    form.userId = 0
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

async function changeRole(userId: number, role: string) {
  try {
    await api.updateMemberRole(orgId.value, userId, role)
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

async function remove(userId: number) {
  if (!confirm('确认移出该成员？')) return
  try {
    await api.removeMember(orgId.value, userId)
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

watch(q, async () => {
  if (!canManage.value) return
  users.value = await api.searchUsers(q.value)
})

onMounted(load)
</script>

<template>
  <div>
    <h1>{{ auth.currentOrgName || '组织详情' }}</h1>
    <p class="sub">机构与报告按组织隔离。成员由系统管理员分配。</p>

    <div v-if="err" class="banner bad">{{ err }}</div>
    <div v-if="msg" class="banner ok">{{ msg }}</div>

    <div v-if="canManage" class="card">
      <h3>拉人入组织</h3>
      <div class="row">
        <input v-model="q" placeholder="搜索用户名/姓名" />
        <select v-model.number="form.userId">
          <option :value="0">选择用户</option>
          <option v-for="u in users" :key="u.id" :value="u.id">{{ u.displayName }}（{{ u.username }}）</option>
        </select>
        <select v-model="form.role">
          <option value="ORG_ADMIN">组织管理员</option>
          <option value="ORG_OPERATOR">经办</option>
          <option value="ORG_REVIEWER">复核</option>
        </select>
        <button class="btn" type="button" @click="add">加入</button>
      </div>
    </div>

    <div class="card table-wrap">
      <h3>成员列表</h3>
      <table>
        <thead>
          <tr><th>姓名</th><th>账号</th><th>角色</th><th>加入时间</th><th v-if="canManage">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="m in members" :key="m.userId">
            <td>{{ m.displayName }}</td>
            <td>{{ m.username }}</td>
            <td>
              <select v-if="canManage" :value="m.role" @change="changeRole(m.userId, ($event.target as HTMLSelectElement).value)">
                <option value="ORG_ADMIN">组织管理员</option>
                <option value="ORG_OPERATOR">经办</option>
                <option value="ORG_REVIEWER">复核</option>
              </select>
              <span v-else>{{ m.role }}</span>
            </td>
            <td>{{ m.joinedAt }}</td>
            <td v-if="canManage">
              <button class="link" type="button" @click="remove(m.userId)">移出</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
h1 { margin: 0 0 6px; font-size: 28px; }
.sub { margin: 0 0 18px; color: var(--muted); }
.banner { border-radius: 16px; padding: 12px 14px; margin-bottom: 14px; font-size: 14px; }
.banner.bad { background: #fde8e4; color: var(--danger); }
.banner.ok { background: #e8f5ec; color: var(--ok); }
.card {
  background: var(--card); border-radius: 24px; padding: 20px; box-shadow: var(--shadow);
  border: 1px solid var(--line); margin-bottom: 16px;
}
.card h3 { margin: 0 0 12px; }
.row { display: flex; flex-wrap: wrap; gap: 10px; }
input, select {
  border: 1px solid var(--line); border-radius: 14px; padding: 10px 12px; background: #faf6f0;
}
.btn {
  border: 0; border-radius: 999px; background: var(--btn); color: #fff;
  padding: 10px 18px; font-weight: 700; cursor: pointer;
}
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th, td { text-align: left; padding: 12px 8px; border-bottom: 1px solid var(--line); }
.link { border: 0; background: transparent; color: var(--danger); cursor: pointer; font-weight: 600; }
</style>
