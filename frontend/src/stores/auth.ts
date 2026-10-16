import { defineStore } from 'pinia'
import { api, type AuthResp } from '../api'

const KEY = 'zhijian_auth'

export const useAuthStore = defineStore('auth', {
  state: () => {
    const raw = localStorage.getItem(KEY)
    const saved = raw ? (JSON.parse(raw) as AuthResp) : null
    return {
      token: saved?.token || '',
      profile: saved as AuthResp | null,
    }
  },
  getters: {
    isLogin: (s) => !!s.token,
    displayName: (s) => s.profile?.displayName || '',
    platformAdmin: (s) => !!s.profile?.platformAdmin,
    currentOrgId: (s) => s.profile?.currentOrgId ?? null,
    currentOrgName: (s) => s.profile?.currentOrgName || '',
    orgs: (s) => s.profile?.orgs || [],
  },
  actions: {
    persist(data: AuthResp) {
      this.token = data.token
      this.profile = data
      localStorage.setItem(KEY, JSON.stringify(data))
    },
    logout() {
      this.token = ''
      this.profile = null
      localStorage.removeItem(KEY)
    },
    async login(username: string, password: string, orgId?: number | null) {
      const data = await api.login({ username, password, orgId: orgId || null })
      this.persist(data)
      return data
    },
    async register(username: string, password: string, displayName: string) {
      const data = await api.register({ username, password, displayName })
      this.persist(data)
      return data
    },
    async refreshMe() {
      if (!this.token) return
      const data = await api.me()
      this.persist(data)
    },
    async switchOrg(orgId: number) {
      const data = await api.switchOrg(orgId)
      this.persist(data)
    },
  },
})
