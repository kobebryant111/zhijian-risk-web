<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const view = ref<any>(null)
const form = reactive({
  llm: { baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o-mini', enabled: false, apiKey: '' },
  baidu: { enabled: false, useInBatch: true, apiKey: '' },
  mcp: { qccEnabled: false, tycEnabled: false, qccApiKey: '', tycApiKey: '' },
})
const saving = ref(false)
const msg = ref('')

async function load() {
  if (!auth.currentOrgId) return
  view.value = await api.getSettings()
  form.llm.baseUrl = view.value.llm.baseUrl
  form.llm.model = view.value.llm.model
  form.llm.enabled = view.value.llm.enabled
  form.baidu.enabled = view.value.baidu.enabled
  form.baidu.useInBatch = view.value.baidu.useInBatch
  form.mcp.qccEnabled = view.value.mcp.qccEnabled
  form.mcp.tycEnabled = view.value.mcp.tycEnabled
}

async function save() {
  saving.value = true; msg.value = ''
  try {
    view.value = await api.saveSettings(form)
    form.llm.apiKey = ''
    form.baidu.apiKey = ''
    form.mcp.qccApiKey = ''
    form.mcp.tycApiKey = ''
    msg.value = '已保存'
  } catch (e) { msg.value = e instanceof Error ? e.message : String(e) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>设置</h1>
      <button class="btn" :disabled="saving || !auth.currentOrgId" @click="save">保存</button>
    </div>
    <div v-if="!auth.currentOrgId" class="panel empty">请选择组织</div>
    <template v-else-if="view">
      <div v-if="msg" class="toast">{{ msg }}</div>
      <div class="grid-2">
        <div class="panel">
          <div class="block-head">
            <h3>大模型</h3>
            <label class="switch">
              <input type="checkbox" v-model="form.llm.enabled" />
              <span>{{ form.llm.enabled ? '已启用' : '未启用' }}</span>
            </label>
          </div>
          <div class="status">{{ view.llm.ready ? '已就绪' : '未就绪' }}{{ view.llm.hasApiKey ? ' · Key 已配置' : '' }}</div>
          <div class="field"><label>Base URL</label><input v-model="form.llm.baseUrl" /></div>
          <div class="field"><label>Model</label><input v-model="form.llm.model" /></div>
          <div class="field"><label>API Key</label><input v-model="form.llm.apiKey" type="password" placeholder="留空则不修改" /></div>
        </div>
        <div class="panel">
          <div class="block-head">
            <h3>百度 AI 搜索</h3>
            <label class="switch">
              <input type="checkbox" v-model="form.baidu.enabled" />
              <span>{{ form.baidu.enabled ? '已启用' : '未启用' }}</span>
            </label>
          </div>
          <div class="status">{{ view.baidu.ready ? '已就绪' : '未就绪' }}</div>
          <label class="switch row">
            <input type="checkbox" v-model="form.baidu.useInBatch" />
            <span>吹哨跑批时使用</span>
          </label>
          <div class="field"><label>API Key</label><input v-model="form.baidu.apiKey" type="password" placeholder="留空则不修改" /></div>

          <div class="block-head" style="margin-top:20px">
            <h3>工商数据源</h3>
          </div>
          <label class="switch row">
            <input type="checkbox" v-model="form.mcp.qccEnabled" />
            <span>企查查</span>
          </label>
          <div class="field"><label>企查查 Key</label><input v-model="form.mcp.qccApiKey" type="password" /></div>
          <label class="switch row">
            <input type="checkbox" v-model="form.mcp.tycEnabled" />
            <span>天眼查</span>
          </label>
          <div class="field"><label>天眼查 Key</label><input v-model="form.mcp.tycApiKey" type="password" /></div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
h3 { margin: 0; font-size: 15px; }
.block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.status { color: var(--muted); font-size: 12px; margin-bottom: 14px; }
.switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
  cursor: pointer;
  user-select: none;
}
.switch.row { margin: 0 0 12px; }
.switch input {
  width: 36px;
  height: 20px;
  appearance: none;
  -webkit-appearance: none;
  background: #dbe1ea;
  border-radius: 999px;
  position: relative;
  cursor: pointer;
  border: 0;
  padding: 0;
  transition: background .15s ease;
}
.switch input::after {
  content: "";
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0,0,0,.15);
  transition: transform .15s ease;
}
.switch input:checked { background: var(--accent); }
.switch input:checked::after { transform: translateX(16px); }
.switch input:focus { box-shadow: none; border: 0; }
.toast {
  margin-bottom: 12px;
  color: var(--ok);
  font-size: 13px;
  font-weight: 600;
}
</style>
