<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const err = ref('')
const busy = ref(false)

async function submit() {
  err.value = ''
  busy.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(username.value.trim(), password.value)
    } else {
      await auth.register(username.value.trim(), password.value, displayName.value.trim() || username.value.trim())
    }
    const redirect = (route.query.redirect as string) || '/app'
    await router.replace(redirect)
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <header class="top">
      <router-link class="brand" to="/">
        <span class="logo">鉴</span>
        <strong>智鉴风控官</strong>
      </router-link>
    </header>

    <div class="card">
      <div class="tabs">
        <button type="button" :class="{ on: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button type="button" :class="{ on: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>
      <h1>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h1>

      <label v-if="mode === 'register'">
        <span>显示名</span>
        <input v-model="displayName" placeholder="张三" />
      </label>
      <label>
        <span>用户名</span>
        <input v-model="username" autocomplete="username" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" @keyup.enter="submit" />
      </label>

      <div v-if="err" class="err">{{ err }}</div>
      <button class="btn" type="button" :disabled="busy" @click="submit">
        {{ busy ? '请稍候…' : mode === 'login' ? '登录' : '注册并进入' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100%;
  background: #f5f7fb;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.top { width: min(420px, 92vw); padding: 36px 0 16px; }
.brand { display: inline-flex; align-items: center; gap: 10px; font-size: 16px; }
.logo {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(145deg, #60a5fa, #2563eb);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 14px;
}
.card {
  width: min(420px, 92vw);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 28px;
  box-shadow: var(--shadow);
}
.tabs { display: flex; gap: 4px; margin-bottom: 18px; background: #f1f4f9; border-radius: 10px; padding: 4px; }
.tabs button {
  flex: 1;
  border: 0;
  background: transparent;
  color: var(--muted);
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
}
.tabs button.on { background: #fff; color: var(--ink); box-shadow: 0 1px 2px rgba(0,0,0,.06); }
h1 { margin: 0 0 20px; font-size: 22px; font-weight: 650; }
label { display: block; margin-bottom: 14px; }
label span {
  display: block;
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
  margin-bottom: 6px;
}
input {
  width: 100%;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  outline: none;
}
input:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}
.err {
  background: #fee2e2;
  color: var(--danger);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  font-size: 13px;
}
.btn {
  width: 100%;
  border: 0;
  border-radius: 8px;
  background: var(--btn);
  color: #fff;
  padding: 12px;
  font-weight: 650;
  cursor: pointer;
}
.btn:disabled { opacity: 0.6; }
</style>
