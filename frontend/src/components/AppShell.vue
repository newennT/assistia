<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { authState, logout } from '@/services/auth'

const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)
const logoutError = ref('')
const displayName = computed(() => `${authState.user?.firstName ?? ''} ${authState.user?.lastName ?? ''}`.trim())
const initials = computed(() => {
  const user = authState.user
  return user ? `${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}`.toUpperCase() : 'A'
})
const titles: Record<string, string> = {
  home: 'Dashboard',
  tickets: 'Tickets',
  'ticket-new': 'Nouveau ticket',
  'ticket-detail': 'Détail du ticket',
}
const pageTitle = computed(() => titles[String(route.name)] ?? 'AssistIA')

const navigation = [
  { label: 'Dashboard', to: '/', icon: '▦', name: 'home', available: true },
  { label: 'Tickets', to: '/tickets', icon: '▤', name: 'tickets', available: true },
  { label: 'Nouveau ticket', to: '/tickets/new', icon: '+', name: 'ticket-new', available: true },
]
const futureNavigation = [
  { label: 'Documentation', icon: '▧' },
  { label: 'Assistant IA', icon: '✳' },
]
const canAdmin = computed(() => authState.user?.role === 'ADMIN')

async function signOut() {
  logoutError.value = ''
  try {
    await logout()
    await router.replace('/login')
  } catch (error) {
    logoutError.value = error instanceof Error ? error.message : 'La déconnexion a échoué.'
  }
}
</script>

<template>
  <div class="app-shell">
    <button class="mobile-menu-toggle" type="button" aria-label="Ouvrir le menu" @click="mobileMenuOpen = !mobileMenuOpen">☰</button>
    <aside class="sidebar" :class="{ 'sidebar-open': mobileMenuOpen }">
      <RouterLink class="brand" to="/" @click="mobileMenuOpen = false">
        <span class="brand-mark">A</span><span>Assist<span class="brand-accent">IA</span></span>
      </RouterLink>
      <div class="workspace-label">ESPACE DE TRAVAIL</div>
      <nav aria-label="Navigation principale" class="side-nav">
        <RouterLink v-for="item in navigation" :key="item.name" :to="item.to" class="nav-link" active-class="nav-active" @click="mobileMenuOpen = false">
          <span class="nav-icon">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      <div class="workspace-label secondary-label">À VENIR</div>
      <div v-for="item in futureNavigation" :key="item.label" class="nav-link nav-disabled" :title="`${item.label} — bientôt disponible`">
        <span class="nav-icon">{{ item.icon }}</span>{{ item.label }}<span class="soon-dot" aria-label="Bientôt disponible"></span>
      </div>
      <RouterLink v-if="canAdmin" class="nav-link admin-link" to="/admin" active-class="nav-active">
        <span class="nav-icon">⚙</span>Administration
      </RouterLink>
      <div class="sidebar-bottom"><span class="online-dot"></span> API connectée par session</div>
    </aside>
    <div v-if="mobileMenuOpen" class="mobile-scrim" @click="mobileMenuOpen = false"></div>
    <main class="main-area">
      <header class="topbar">
        <div><div class="breadcrumb">AssistIA <span>/</span> {{ pageTitle }}</div><h1>{{ pageTitle }}</h1></div>
        <div class="profile-area">
          <div class="profile-copy"><strong>{{ displayName || 'Utilisateur' }}</strong><span>{{ authState.user?.role }}</span></div>
          <div class="avatar" aria-hidden="true">{{ initials }}</div>
          <button class="signout-button" type="button" @click="signOut">Déconnexion</button>
        </div>
      </header>
      <section class="page-content"><p v-if="logoutError" class="form-error logout-error" role="alert">{{ logoutError }}</p><RouterView /></section>
    </main>
  </div>
</template>
