<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const switching = ref(false)
const menuOpen = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const orgOpen = ref(false)
const orgRef = ref<HTMLElement | null>(null)
const pwdOpen = ref(false)
const pwdBusy = ref(false)
const pwdErr = ref('')
const pwdOk = ref('')
const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })

/** 与桌面端一致的 8 个模块 */
const nav = [
  { to: '/app/overview', label: '总览', icon: 'overview' },
  { to: '/app/whistle', label: '风险吹哨', icon: 'whistle' },
  { to: '/app/finance', label: '经营守护', icon: 'finance' },
  { to: '/app/admission', label: '准入瞭望', icon: 'admission' },
  { to: '/app/partners', label: '机构名单', icon: 'partners' },
  { to: '/app/rules', label: '规则库', icon: 'rules' },
  { to: '/app/agent', label: '问鉴控', icon: 'agent' },
  { to: '/app/orgs', label: '组织', icon: 'orgs' },
  { to: '/app/settings', label: '设置', icon: 'settings' },
]

const roleLabel = computed(() => {
  if (auth.platformAdmin) return '平台管理员'
  const o = auth.orgs.find((x) => x.id === auth.currentOrgId)
  const role = o?.role || ''
  if (role === 'ORG_ADMIN') return '组织管理员'
  if (role === 'ORG_REVIEWER') return '复核员'
  if (role === 'ORG_OPERATOR') return '经办'
  return role || '成员'
})

onMounted(async () => {
  try { await auth.refreshMe() } catch { /* token expired */ }
  document.addEventListener('click', onDocClick)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
})

function onDocClick(e: MouseEvent) {
  const t = e.target as Node
  if (menuOpen.value && menuRef.value && !menuRef.value.contains(t)) menuOpen.value = false
  if (orgOpen.value && orgRef.value && !orgRef.value.contains(t)) orgOpen.value = false
}

function toggleMenu(e: MouseEvent) {
  e.stopPropagation()
  orgOpen.value = false
  menuOpen.value = !menuOpen.value
}

function toggleOrg(e: MouseEvent) {
  e.stopPropagation()
  menuOpen.value = false
  orgOpen.value = !orgOpen.value
}

async function switchOrgFromMenu(id: number) {
  if (!id || id === auth.currentOrgId) {
    menuOpen.value = false
    return
  }
  switching.value = true
  try {
    await auth.switchOrg(id)
    menuOpen.value = false
    orgOpen.value = false
  } catch (err) {
    alert(err instanceof Error ? err.message : String(err))
  } finally {
    switching.value = false
  }
}

function go(path: string) {
  menuOpen.value = false
  orgOpen.value = false
  router.push(path)
}

function openPassword() {
  menuOpen.value = false
  pwdErr.value = ''
  pwdOk.value = ''
  pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
  pwdOpen.value = true
}

async function submitPassword() {
  pwdErr.value = ''
  pwdOk.value = ''
  if (pwd.value.newPassword.length < 6) {
    pwdErr.value = '新密码至少 6 位'
    return
  }
  if (pwd.value.newPassword !== pwd.value.confirm) {
    pwdErr.value = '两次输入的新密码不一致'
    return
  }
  pwdBusy.value = true
  try {
    await api.changePassword({
      oldPassword: pwd.value.oldPassword,
      newPassword: pwd.value.newPassword,
    })
    pwdOk.value = '密码已更新'
    pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
  } catch (e) {
    pwdErr.value = e instanceof Error ? e.message : String(e)
  } finally {
    pwdBusy.value = false
  }
}

function logout() {
  menuOpen.value = false
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand">
        <img class="logo" src="/brand/logo.jpg" alt="" />
        <b>智鉴风控官</b>
      </div>
      <div class="org-switch" ref="orgRef">
        <router-link v-if="!auth.orgs.length && auth.platformAdmin" class="org-create" to="/app/orgs">创建组织</router-link>
        <span v-else-if="!auth.orgs.length" class="org-wait">等待管理员分配组织</span>
        <template v-else>
          <button type="button" class="org-trigger" :class="{ open: orgOpen }" :disabled="switching" @click="toggleOrg">
            <span>{{ auth.currentOrgName || '选择组织' }}</span>
            <i class="chev" aria-hidden="true"></i>
          </button>
          <div v-if="orgOpen" class="org-panel" role="menu">
            <button
              v-for="o in auth.orgs"
              :key="o.id"
              type="button"
              class="org-item"
              :class="{ on: o.id === auth.currentOrgId }"
              :disabled="switching"
              @click="switchOrgFromMenu(o.id)"
            >
              {{ o.name }}
            </button>
            <button type="button" class="org-manage" @click="go('/app/orgs')">管理组织</button>
          </div>
        </template>
      </div>
      <div class="bar-right" ref="menuRef">
          <button type="button" class="avatar-btn" :aria-expanded="menuOpen" title="账号" @click="toggleMenu">
            <img class="avatar" src="/brand/avatar-default.jpg" alt="" />
          </button>

          <div v-if="menuOpen" class="menu" role="menu">
            <div class="menu-head">
              <div class="menu-name">{{ auth.displayName || auth.profile?.username }}</div>
              <div class="menu-sub">
                <span>{{ auth.profile?.username }}</span>
                <span class="sep">|</span>
                <span>{{ roleLabel }}</span>
              </div>
            </div>

            <div class="menu-card" v-if="auth.currentOrgName || auth.orgs.length">
              <div class="menu-card-title">当前组织</div>
              <div class="menu-card-org">{{ auth.currentOrgName || '未选择' }}</div>
            </div>

            <div class="menu-list">
              <button type="button" @click="openPassword">修改密码</button>
              <button type="button" @click="go('/app/settings')">账号设置</button>
              <button type="button" @click="go('/app/orgs')">组织管理</button>
              <button type="button" @click="go('/app/clues')">线索台账</button>
            </div>

            <div class="menu-orgs" v-if="auth.orgs.length > 1">
              <div class="menu-orgs-title">切换组织</div>
              <button
                v-for="o in auth.orgs"
                :key="o.id"
                type="button"
                class="org-item"
                :class="{ on: o.id === auth.currentOrgId }"
                :disabled="switching"
                @click="switchOrgFromMenu(o.id)"
              >
                {{ o.name }}
              </button>
            </div>

            <div class="menu-foot">
              <button type="button" class="danger" @click="logout">退出登录</button>
            </div>
          </div>
      </div>
    </header>

    <div class="body">
      <aside class="side">
        <nav>
          <router-link v-for="n in nav" :key="n.to" :to="n.to" class="nav-item">
            <span class="ico" :data-icon="n.icon" aria-hidden="true"></span>
            <span>{{ n.label }}</span>
          </router-link>
        </nav>
      </aside>
      <section class="canvas">
        <router-view :key="String(auth.currentOrgId || 'none')" />
      </section>
      <div v-if="pwdOpen" class="mask" @click.self="pwdOpen = false">
        <div class="dialog" role="dialog">
          <h3>修改密码</h3>
          <div class="field"><label>原密码</label><input v-model="pwd.oldPassword" type="password" autocomplete="current-password" /></div>
          <div class="field"><label>新密码</label><input v-model="pwd.newPassword" type="password" autocomplete="new-password" /></div>
          <div class="field"><label>确认新密码</label><input v-model="pwd.confirm" type="password" autocomplete="new-password" @keyup.enter="submitPassword" /></div>
          <div v-if="pwdErr" class="pwd-msg bad">{{ pwdErr }}</div>
          <div v-if="pwdOk" class="pwd-msg ok">{{ pwdOk }}</div>
          <div class="dialog-actions">
            <button class="btn secondary" type="button" @click="pwdOpen = false">关闭</button>
            <button class="btn" type="button" :disabled="pwdBusy" @click="submitPassword">{{ pwdBusy ? '保存中…' : '保存' }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shell {
  --side: 168px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 16px 0 12px;
  background: #fff;
  border-bottom: 1px solid #eef1f5;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 20;
}
.brand {
  width: calc(var(--side) - 12px);
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
  box-sizing: border-box;
}
.brand b {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: #1d2430;
}
.logo {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  object-fit: cover;
  flex-shrink: 0;
  display: block;
}
.body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}
.side {
  width: var(--side);
  background: #fff;
  border-right: 1px solid #eef1f5;
  padding: 10px 8px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: auto;
  flex: 1;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 8px;
  color: #3d4654;
  font-weight: 500;
  font-size: 14px;
}
.nav-item:hover { background: #f4f7fb; color: #1d2430; }
.nav-item.router-link-active {
  background: #eaf2ff;
  color: #1d6fe8;
  font-weight: 650;
}
.ico {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  background: currentColor;
  opacity: 0.9;
  -webkit-mask: center / contain no-repeat;
  mask: center / contain no-repeat;
}
.ico[data-icon="overview"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M12 3 20 12l-8 9-8-9 8-9Z'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M12 3 20 12l-8 9-8-9 8-9Z'/%3E%3C/svg%3E");
}
.ico[data-icon="whistle"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M5 20V9l7-5 7 5v4'/%3E%3Cpath d='M5 14h7l3 6'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M5 20V9l7-5 7 5v4'/%3E%3Cpath d='M5 14h7l3 6'/%3E%3C/svg%3E");
}
.ico[data-icon="finance"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Ccircle cx='12' cy='12' r='8'/%3E%3Ccircle cx='12' cy='12' r='3.5'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Ccircle cx='12' cy='12' r='8'/%3E%3Ccircle cx='12' cy='12' r='3.5'/%3E%3C/svg%3E");
}
.ico[data-icon="admission"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Ccircle cx='12' cy='12' r='8'/%3E%3Ccircle cx='12' cy='12' r='2.5' fill='black'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Ccircle cx='12' cy='12' r='8'/%3E%3Ccircle cx='12' cy='12' r='2.5' fill='black'/%3E%3C/svg%3E");
}
.ico[data-icon="partners"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M5 7h14M5 12h14M5 17h10'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M5 7h14M5 12h14M5 17h10'/%3E%3C/svg%3E");
}
.ico[data-icon="rules"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M7 5h12v14H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z'/%3E%3Cpath d='M9 9h7M9 13h7M9 17h4'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M7 5h12v14H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z'/%3E%3Cpath d='M9 9h7M9 13h7M9 17h4'/%3E%3C/svg%3E");
}
.ico[data-icon="agent"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M12 3 13.8 9.2 20 11l-6.2 1.8L12 19l-1.8-6.2L4 11l6.2-1.8L12 3Z'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M12 3 13.8 9.2 20 11l-6.2 1.8L12 19l-1.8-6.2L4 11l6.2-1.8L12 3Z'/%3E%3C/svg%3E");
}
.ico[data-icon="orgs"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M4 20V8l8-4 8 4v12'/%3E%3Cpath d='M9 20v-6h6v6'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Cpath d='M4 20V8l8-4 8 4v12'/%3E%3Cpath d='M9 20v-6h6v6'/%3E%3C/svg%3E");
}
.ico[data-icon="settings"] {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Ccircle cx='12' cy='12' r='3'/%3E%3Cpath d='M12 4.5v2M12 17.5v2M4.5 12h2M17.5 12h2M6.5 6.5l1.4 1.4M16.1 16.1l1.4 1.4M17.5 6.5l-1.4 1.4M7.9 16.1l-1.4 1.4'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='1.8'%3E%3Ccircle cx='12' cy='12' r='3'/%3E%3Cpath d='M12 4.5v2M12 17.5v2M4.5 12h2M17.5 12h2M6.5 6.5l1.4 1.4M16.1 16.1l1.4 1.4M17.5 6.5l-1.4 1.4M7.9 16.1l-1.4 1.4'/%3E%3C/svg%3E");
}
.org-switch { position: relative; display: flex; align-items: center; min-width: 0; }
.org-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 280px;
  height: 32px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #1d2430;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}
.org-trigger span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.org-trigger:hover,
.org-trigger.open { background: #f4f7fb; }
.chev {
  width: 0;
  height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid #8b95a5;
  flex-shrink: 0;
}
.org-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  min-width: 240px;
  max-width: 320px;
  padding: 6px;
  background: #fff;
  border: 1px solid #e7ebf1;
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(17, 24, 39, 0.12);
  z-index: 40;
}
.org-panel .org-item,
.org-manage {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  border-radius: 6px;
  padding: 9px 10px;
  font-size: 14px;
  color: #1d2430;
  cursor: pointer;
}
.org-panel .org-item:hover,
.org-manage:hover { background: #f4f7fb; }
.org-panel .org-item.on {
  background: #eaf2ff;
  color: #1d6fe8;
  font-weight: 650;
}
.org-manage {
  margin-top: 4px;
  border-top: 1px solid #eef1f5;
  border-radius: 0 0 6px 6px;
  color: #8b95a5;
  font-size: 13px;
}
.org-create {
  margin-left: 4px;
  padding: 6px 12px;
  border-radius: 8px;
  background: #eaf2ff;
  color: #1d6fe8;
  font-size: 13px;
  font-weight: 650;
}
.org-create:hover { background: #dce9ff; }
.org-wait { margin-left: 8px; color: var(--muted); font-size: 13px; }
.pwd-msg { margin: 0 0 10px; font-size: 13px; }
.pwd-msg.bad { color: var(--danger); }
.pwd-msg.ok { color: var(--ok); }
.bar-right {
  position: relative;
  margin-left: auto;
  display: flex;
  align-items: center;
}
.avatar-btn {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  border-radius: 50%;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}
.avatar-btn:hover .avatar { opacity: 0.88; }

.menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 300px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(31, 42, 55, 0.14);
  z-index: 50;
  overflow: hidden;
}
.menu-head { padding: 16px 16px 12px; }
.menu-name { font-size: 16px; font-weight: 700; color: #1f2430; }
.menu-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
  display: flex;
  align-items: center;
  gap: 6px;
}
.sep { opacity: 0.5; }
.menu-card {
  margin: 0 12px 10px;
  padding: 12px 14px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2f7cf6, #1d6fe8);
  color: #fff;
}
.menu-card-title { font-size: 12px; opacity: 0.85; }
.menu-card-org { margin-top: 4px; font-size: 14px; font-weight: 650; }
.menu-list {
  display: flex;
  flex-direction: column;
  padding: 4px 8px;
  border-top: 1px solid var(--line);
}
.menu-list button,
.menu-orgs .org-item,
.menu-foot button {
  border: 0;
  background: transparent;
  text-align: left;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #3a4150;
  width: 100%;
}
.menu-list button:hover,
.menu-orgs .org-item:hover,
.menu-foot button:hover {
  background: #f5f7fb;
}
.menu-orgs {
  padding: 8px 8px 4px;
  border-top: 1px solid var(--line);
}
.menu-orgs-title {
  padding: 4px 12px 6px;
  font-size: 12px;
  color: var(--muted);
}
.org-item.on {
  color: #1d6fe8;
  background: #eaf2ff;
  font-weight: 650;
}
.menu-foot {
  padding: 4px 8px 10px;
  border-top: 1px solid var(--line);
}
.menu-foot .danger { color: #dc2626; }
.canvas {
  flex: 1;
  min-width: 0;
  background: #f3f5f8;
  padding: 16px 20px 20px;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.canvas > * {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
</style>
