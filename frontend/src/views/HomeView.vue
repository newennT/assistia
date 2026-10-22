<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { isApiErrorStatus } from '@/services/api'
import { getTickets } from '@/services/tickets'
import { authState, clearSession } from '@/services/auth'
import type { Ticket } from '@/types/ticket'
import { getTicketStats, recentTickets, ticketPriorities } from '@/utils/ticketStats'

const route = useRoute()
const router = useRouter()
const tickets = ref<Ticket[]>([])
const loading = ref(true)
const error = ref('')
const stats = computed(() => getTicketStats(tickets.value))
const latestTickets = computed(() => recentTickets(tickets.value))
const priorityMax = computed(() => Math.max(1, ...ticketPriorities.map((priority) => stats.value.priorities[priority])))
const priorityLabels = { CRITICAL: 'Critique', HIGH: 'Haute', MEDIUM: 'Moyenne', LOW: 'Basse' }
const statusLabels = { OPEN: 'Ouvert', IN_PROGRESS: 'En cours', PENDING: 'En attente', RESOLVED: 'Résolu', CLOSED: 'Fermé' }

function formatDate(value: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

async function loadTickets() {
  loading.value = true
  error.value = ''
  try {
    tickets.value = await getTickets()
  } catch (cause) {
    if (isApiErrorStatus(cause, 401)) {
      clearSession()
      await router.replace({ name: 'login', query: { redirect: route.fullPath, expired: 'true' } })
      return
    }
    error.value = isApiErrorStatus(cause, 403)
      ? 'Vous n’avez pas accès aux tickets.'
      : cause instanceof Error ? cause.message : 'Impossible de charger les tickets. Réessayez.'
  } finally {
    loading.value = false
  }
}

onMounted(loadTickets)
</script>

<template>
  <div class="dashboard-page">
    <section class="welcome-panel">
      <div class="welcome-copy">
        <span class="eyebrow">ESPACE SUPPORT</span>
        <h2>Bonjour {{ authState.user?.firstName }},</h2>
        <p>Voici l’activité des tickets auxquels votre compte a accès.</p>
      </div>
      <div class="welcome-orb" aria-hidden="true">A</div>
    </section>

    <div v-if="loading" class="state-card" role="status"><span class="spinner" aria-hidden="true"></span>Chargement du dashboard…</div>
    <div v-else-if="error" class="state-card error-state" role="alert">
      <span>{{ error }}</span><button class="secondary-button" type="button" @click="loadTickets">Réessayer</button>
    </div>
    <template v-else>
      <section aria-label="Indicateurs des tickets" class="stats-grid">
        <article class="stat-card"><span class="stat-icon stat-open">▤</span><div><span>Ouverts</span><strong>{{ stats.open }}</strong></div></article>
        <article class="stat-card"><span class="stat-icon stat-progress">◷</span><div><span>En cours</span><strong>{{ stats.inProgress }}</strong></div></article>
        <article class="stat-card"><span class="stat-icon stat-resolved">✓</span><div><span>Résolus</span><strong>{{ stats.resolved }}</strong></div></article>
      </section>

      <div class="dashboard-columns">
        <section class="panel priority-panel">
          <div class="section-heading"><div><h2>Répartition par priorité</h2><p>{{ tickets.length }} ticket{{ tickets.length > 1 ? 's' : '' }} dans votre périmètre</p></div></div>
          <div class="priority-list">
            <div v-for="priority in ticketPriorities" :key="priority" class="priority-row">
              <span>{{ priorityLabels[priority] }}</span>
              <div class="priority-track" aria-hidden="true"><span :class="`priority-fill priority-${priority.toLowerCase()}`" :style="{ width: `${(stats.priorities[priority] / priorityMax) * 100}%` }"></span></div>
              <strong>{{ stats.priorities[priority] }}</strong>
            </div>
          </div>
        </section>

        <section class="panel recent-panel">
          <div class="section-heading"><div><h2>Tickets récemment mis à jour</h2><p>Les cinq mises à jour les plus récentes</p></div><RouterLink class="text-link" to="/tickets">Tous les tickets <span aria-hidden="true">→</span></RouterLink></div>
          <div v-if="latestTickets.length" class="recent-list">
            <RouterLink v-for="ticket in latestTickets" :key="ticket.id" class="recent-ticket" :to="`/tickets/${ticket.id}`">
              <span class="recent-ticket-copy"><strong>{{ ticket.title }}</strong><small>{{ ticket.category.replace(/_/g, ' ') }} · {{ formatDate(ticket.updatedAt) }}</small></span>
              <span class="status-badge" :class="`status-${ticket.status.toLowerCase()}`">{{ statusLabels[ticket.status] }}</span>
            </RouterLink>
          </div>
          <p v-else class="empty-copy">Aucun ticket pour le moment.</p>
        </section>
      </div>
    </template>
  </div>
</template>
