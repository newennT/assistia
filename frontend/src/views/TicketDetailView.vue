<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isApiErrorStatus } from '@/services/api'
import { getTicket } from '@/services/tickets'
import { clearSession } from '@/services/auth'
import type { Ticket, TicketPriority, TicketStatus } from '@/types/ticket'

const route = useRoute()
const router = useRouter()
const ticket = ref<Ticket | null>(null)
const loading = ref(true)
const error = ref('')
const categoryLabels: Record<Ticket['category'], string> = {
  WINDOWS: 'Windows', LINUX: 'Linux', DNS: 'DNS', DHCP: 'DHCP', VPN: 'VPN', NETWORK: 'Réseau', PRINTER: 'Imprimante',
  USER_ACCOUNT: 'Compte utilisateur', PERMISSIONS: 'Autorisations', M365: 'M365', BUSINESS_APP: 'Application métier', GENERAL: 'Général', OTHER: 'Autre',
}
const statusLabels: Record<TicketStatus, string> = { OPEN: 'Ouvert', IN_PROGRESS: 'En cours', PENDING: 'En attente', RESOLVED: 'Résolu', CLOSED: 'Fermé' }
const priorityLabels: Record<TicketPriority, string> = { LOW: 'Basse', MEDIUM: 'Moyenne', HIGH: 'Haute', CRITICAL: 'Critique' }

function formatDate(value: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' }).format(new Date(value))
}

async function loadTicket() {
  loading.value = true
  error.value = ''
  ticket.value = null
  const id = Number(route.params.id)
  try {
    ticket.value = await getTicket(id)
  } catch (cause) {
    if (isApiErrorStatus(cause, 401)) {
      clearSession()
      await router.replace({ name: 'login', query: { redirect: route.fullPath, expired: 'true' } })
      return
    }
    error.value = isApiErrorStatus(cause, 404)
      ? 'Ce ticket est introuvable ou n’est pas accessible à votre compte.'
      : isApiErrorStatus(cause, 403)
        ? 'Vous n’avez pas accès à ce ticket.'
        : cause instanceof Error ? cause.message : 'Impossible de charger ce ticket. Réessayez.'
  } finally {
    loading.value = false
  }
}

onMounted(loadTicket)
watch(() => route.params.id, loadTicket)
</script>

<template>
  <div class="ticket-detail-page">
    <RouterLink class="back-link" to="/tickets">← Retour aux tickets</RouterLink>
    <div v-if="loading" class="state-card" role="status"><span class="spinner" aria-hidden="true"></span>Chargement du ticket…</div>
    <div v-else-if="error" class="state-card error-state" role="alert"><span>{{ error }}</span><button class="secondary-button" type="button" @click="loadTicket">Réessayer</button></div>
    <article v-else-if="ticket" class="panel ticket-detail-card">
      <div class="detail-heading">
        <div><span class="eyebrow">TICKET #{{ ticket.id }}</span><h2>{{ ticket.title }}</h2></div>
        <span class="status-badge" :class="`status-${ticket.status.toLowerCase()}`">{{ statusLabels[ticket.status] }}</span>
      </div>
      <div class="detail-meta">
        <div><span>Catégorie</span><strong>{{ categoryLabels[ticket.category] }}</strong></div>
        <div><span>Priorité</span><strong><span class="priority-badge" :class="`priority-badge-${ticket.priority.toLowerCase()}`">{{ priorityLabels[ticket.priority] }}</span></strong></div>
        <div><span>Créé le</span><strong>{{ formatDate(ticket.createdAt) }}</strong></div>
        <div><span>Mis à jour</span><strong>{{ formatDate(ticket.updatedAt) }}</strong></div>
      </div>
      <section class="description-section"><h3>Description</h3><p>{{ ticket.description }}</p></section>
    </article>
  </div>
</template>
