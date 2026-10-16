import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'landing', component: () => import('../views/Landing.vue') },
    { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
    {
      path: '/app',
      component: () => import('../views/AppLayout.vue'),
      meta: { auth: true },
      children: [
        { path: '', redirect: '/app/overview' },
        { path: 'overview', name: 'overview', component: () => import('../views/Overview.vue') },
        { path: 'whistle', name: 'whistle', component: () => import('../views/Whistle.vue') },
        { path: 'clues', name: 'clues', component: () => import('../views/Clues.vue') },
        { path: 'admission', name: 'admission', component: () => import('../views/Admission.vue') },
        { path: 'finance', name: 'finance', component: () => import('../views/Finance.vue') },
        { path: 'rules', name: 'rules', component: () => import('../views/Rules.vue') },
        { path: 'agent', name: 'agent', component: () => import('../views/Agent.vue') },
        { path: 'partners', name: 'partners', component: () => import('../views/Partners.vue') },
        { path: 'settings', name: 'settings', component: () => import('../views/Settings.vue') },
        { path: 'orgs', name: 'orgs', component: () => import('../views/Orgs.vue') },
        { path: 'orgs/:id', name: 'org-detail', component: () => import('../views/OrgDetail.vue') },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.auth && !auth.isLogin) return { name: 'login', query: { redirect: to.fullPath } }
  return true
})

export default router
