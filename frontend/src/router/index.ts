import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import LoginView from '@/views/LoginView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import { authState, initializeSession } from '@/services/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
    {
      path: '/', component: AppShell, meta: { requiresAuth: true },
      children: [
        { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
        { path: 'tickets', name: 'tickets', component: () => import('@/views/TicketsView.vue') },
        { path: 'tickets/new', name: 'ticket-new', component: PlaceholderView, props: { title: 'Nouveau ticket', description: 'Le formulaire de création sera ajouté dans la prochaine étape.' } },
        { path: 'tickets/:id', name: 'ticket-detail', component: () => import('@/views/TicketDetailView.vue') },
        { path: 'admin', name: 'admin', component: PlaceholderView, props: { title: 'Administration', description: 'Les outils d’administration ne sont pas encore développés.' }, meta: { roles: ['ADMIN'] } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  if (!authState.ready) {
    try {
      await initializeSession()
    } catch {
      return { name: 'login', query: { redirect: to.fullPath, unavailable: 'true' } }
    }
  }

  if (to.meta.requiresAuth && !authState.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && authState.user) return { name: 'home' }

  const roles = to.meta.roles as string[] | undefined
  if (roles?.length && (!authState.user || !roles.includes(authState.user.role))) return { name: 'home' }
  return true
})

export default router
