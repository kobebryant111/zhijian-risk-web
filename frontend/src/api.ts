import axios from 'axios'
import { useAuthStore } from './stores/auth'

const http = axios.create({ baseURL: '/api', timeout: 60000 })

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// 后端统一返回 { code, message, data }。这里取出 data，页面仍按业务对象使用。
http.interceptors.response.use(
  (r) => {
    const body = r.data
    if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
      r.data = body.data
    }
    return r
  },
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  },
)

export type OrgBrief = { id: number; code: string; name: string; role: string }
export type AuthResp = {
  token: string
  userId: number
  username: string
  displayName: string
  platformAdmin: boolean
  currentOrgId: number | null
  currentOrgName: string | null
  currentOrgRole: string | null
  orgs: OrgBrief[]
}
export type OrgDetail = {
  id: number; code: string; name: string; description: string; enabled: boolean; memberCount: number
}
export type MemberView = {
  userId: number; username: string; displayName: string; role: string; joinedAt: string
}
export type UserBrief = { id: number; username: string; displayName: string }
export type PartnerView = {
  id: number; name: string; alias: string; partnerType: string; status: string; uscc: string; updatedAt: string
}

export const api = {
  register: (body: { username: string; password: string; displayName: string }) =>
    http.post<AuthResp>('/auth/register', body).then((r) => r.data),
  login: (body: { username: string; password: string; orgId?: number | null }) =>
    http.post<AuthResp>('/auth/login', body).then((r) => r.data),
  me: () => http.get<AuthResp>('/auth/me').then((r) => r.data),
  changePassword: (body: { oldPassword: string; newPassword: string }) =>
    http.post('/auth/password', body).then((r) => r.data),
  switchOrg: (orgId: number) => http.post<AuthResp>('/auth/switch-org', { orgId }).then((r) => r.data),
  listOrgs: () => http.get<OrgDetail[]>('/orgs').then((r) => r.data),
  createOrg: (body: { code: string; name: string; description?: string }) =>
    http.post<OrgDetail>('/orgs', body).then((r) => r.data),
  members: (orgId: number) => http.get<MemberView[]>(`/orgs/${orgId}/members`).then((r) => r.data),
  addMember: (orgId: number, body: { userId: number; role: string }) =>
    http.post(`/orgs/${orgId}/members`, body).then((r) => r.data),
  updateMemberRole: (orgId: number, userId: number, role: string) =>
    http.patch(`/orgs/${orgId}/members/${userId}`, { role }).then((r) => r.data),
  removeMember: (orgId: number, userId: number) =>
    http.delete(`/orgs/${orgId}/members/${userId}`).then((r) => r.data),
  searchUsers: (q = '') => http.get<UserBrief[]>('/orgs/users/search', { params: { q } }).then((r) => r.data),
  listPartners: () => http.get<PartnerView[]>('/partners').then((r) => r.data),
  createPartner: (body: object) => http.post<PartnerView>('/partners', body).then((r) => r.data),
  updatePartner: (id: number, body: object) => http.put<PartnerView>(`/partners/${id}`, body).then((r) => r.data),
  deletePartner: (id: number) => http.delete(`/partners/${id}`).then((r) => r.data),

  overview: () => http.get<any>('/dashboard/overview').then((r) => r.data),
  listClues: (params?: object) => http.get<any[]>('/clues', { params }).then((r) => r.data),
  createClue: (body: object) => http.post<any>('/clues', body).then((r) => r.data),
  updateClue: (id: number, body: object) => http.patch<any>(`/clues/${id}`, body).then((r) => r.data),
  listRules: () => http.get<any[]>('/rules').then((r) => r.data),
  createRule: (body: object) => http.post<any>('/rules', body).then((r) => r.data),
  updateRule: (id: number, body: object) => http.put<any>(`/rules/${id}`, body).then((r) => r.data),
  deleteRule: (id: number) => http.delete(`/rules/${id}`).then((r) => r.data),
  listWhistleReports: () => http.get<any[]>('/whistle/reports').then((r) => r.data),
  getWhistleReport: (id: number) => http.get<any>(`/whistle/reports/${id}`).then((r) => r.data),
  startWhistleJob: (body: object = {}) => http.post<any>('/whistle/jobs', body).then((r) => r.data),
  listAdmission: () => http.get<any[]>('/admission/reviews').then((r) => r.data),
  getAdmission: (id: number) => http.get<any>(`/admission/reviews/${id}`).then((r) => r.data),
  startAdmission: (body: object) => http.post<any>('/admission/reviews', body).then((r) => r.data),
  listFinance: () => http.get<any[]>('/finance/reports').then((r) => r.data),
  getFinance: (id: number) => http.get<any>(`/finance/reports/${id}`).then((r) => r.data),
  startFinance: (body: object) => http.post<any>('/finance/reports', body).then((r) => r.data),
  listConversations: () => http.get<any[]>('/agent/conversations').then((r) => r.data),
  getConversation: (id: number) => http.get<any>(`/agent/conversations/${id}`).then((r) => r.data),
  createConversation: (title?: string) => http.post<any>('/agent/conversations', { title }).then((r) => r.data),
  sendMessage: (id: number, content: string) => http.post<any>(`/agent/conversations/${id}/messages`, { content }).then((r) => r.data),
  deleteConversation: (id: number) => http.delete(`/agent/conversations/${id}`).then((r) => r.data),
  getSettings: () => http.get<any>('/settings').then((r) => r.data),
  saveSettings: (body: object) => http.put<any>('/settings', body).then((r) => r.data),
}
