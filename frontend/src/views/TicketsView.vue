<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { isApiErrorStatus } from '@/services/api'
import { getTickets } from '@/services/tickets'
import { clearSession } from '@/services/auth'
import type { Ticket, TicketPriority, TicketStatus } from '@/types/ticket'

const route = useRoute()
const router = useRouter()
const status = ref<TicketStatus | ''>('')
const priority = ref<TicketPriority | ''>('')
const tickets = ref<Ticket[]>([])
const loading = ref(true)
const error = ref('')
let requestNumber = 0

const statusOptions: { value: TicketStatus; label: string }[] = [
  { value: 'OPEN', label: 'Ouvert' },
  { value: 'IN_PROGRESS', label: 'En cours' },
  { value: 'PENDING', label: 'En attente' },
  { value: 'RESOLVED', label: 'Résolu' },
  { value: 'CLOSED', label: 'Fermé' },
]
const priorityOptions: { value: TicketPriority; label: string }[] = [
  { value: 'CRITICAL', label: 'Critique' },
  { value: 'HIGH', label: 'Haute' },
  { value: 'MEDIUM', label: 'Moyenne' },
  { value: 'LOW', label: 'Basse' },
]
const categoryLabels: Record<Ticket['category'], string> = {
  WINDOWS: 'Windows', LINUX: 'Linux', DNS: 'DNS', DHCP: 'DHCP', VPN: 'VPN', NETWORK: 'Réseau',
  PRINTER: 'Imprimante', USER_ACCOUNT: 'Compte utilisateur', PERMISSIONS: 'Autorisations', M365: 'M365',
  BUSINESS_APP: 'Application métier', GENERAL: 'Général', OTHER: 'Autre',
}
const statusLabels: Record<TicketStatus, string> = {
  OPEN: 'Ouvert', IN_PROGRESS: 'En cours', PENDING: 'En attente', RESOLVED: 'Résolu', CLOSED: 'Fermé',
}
const priorityLabels: Record<TicketPriority, string> = { LOW: 'Basse', MEDIUM: 'Moyenne', HIGH: 'Haute', CRITICAL: 'Critique' }

function formatDate(value: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

async function loadTickets() {
  const currentRequest = ++requestNumber
  loading.value = true
  error.value = ''
  try {
    const result = await getTickets({ status: status.value || undefined, priority: priority.value || undefined })
    if (currentRequest === requestNumber) tickets.value = result
  } catch (cause) {
    if (currentRequest !== requestNumber) return
    if (isApiErrorStatus(cause, 401)) {
      clearSession()
      await router.replace({ name: 'login', query: { redirect: route.fullPath, expired: 'true' } })
      return
    }
    error.value = isApiErrorStatus(cause, 403)
      ? 'Vous n’avez pas accès à ces tickets.'
      : cause instanceof Error ? cause.message : 'Impossible de charger les tickets. Réessayez.'
    tickets.value = []
  } finally {
    if (currentRequest === requestNumber) loading.value = false
  }
}

function resetFilters() {
  status.value = ''
  priority.value = ''
}

watch([status, priority], loadTickets, { immediate: true })
</script>

<template>
  <div class="tickets-page">
    <div class="page-intro">
      <div><span class="eyebrow">ESPACE SUPPORT</span><h2>Suivi des tickets</h2><p>Consultez les demandes de support accessibles à votre compte.</p></div>
      <span v-if="!loading && !error" class="result-count">{{ tickets.length }} résultat{{ tickets.length > 1 ? 's' : '' }}</span>
    </div>

    <section class="panel ticket-list-panel">
      <div class="filters" aria-label="Filtres des tickets">
        <label>Statut
          <select v-model="status" aria-label="Filtrer par statut">
            <option value="">Tous les statuts</option>
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label>Priorité
          <select v-model="priority" aria-label="Filtrer par priorité">
            <option value="">Toutes les priorités</option>
            <option v-for="option in priorityOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <button class="secondary-button reset-filters" type="button" :disabled="!status && !priority" @click="resetFilters">Réinitialiser</button>
      </div>

      <div v-if="loading" class="state-card inline-state" role="status"><span class="spinner" aria-hidden="true"></span>Chargement des tickets…</div>
      <div v-else-if="error" class="state-card inline-state error-state" role="alert"><span>{{ error }}</span><button class="secondary-button" type="button" @click="loadTickets">Réessayer</button></div>
      <div v-else-if="tickets.length === 0" class="empty-state">
        <span class="empty-state-icon" aria-hidden="true">▤</span>
        <h3>{{ status || priority ? 'Aucun résultat' : 'Aucun ticket' }}</h3>
        <p>{{ status || priority ? 'Aucun ticket ne correspond à ces filtres.' : 'Aucun ticket n’est disponible dans votre périmètre.' }}</p>
        <button v-if="status || priority" class="text-button" type="button" @click="resetFilters">Effacer les filtres</button>
      </div>
      <div v-else class="table-wrap">
        <table class="ticket-table">
          <thead><tr><th scope="col">Ticket</th><th scope="col">Catégorie</th><th scope="col">Priorité</th><th scope="col">Statut</th><th scope="col">Mis à jour</th></tr></thead>
          <tbody>
            <tr v-for="ticket in tickets" :key="ticket.id">
              <td data-label="Ticket"><RouterLink class="ticket-title-link" :to="`/tickets/${ticket.id}`"><strong>{{ ticket.title }}</strong><small>#{{ ticket.id }}</small></RouterLink></td>
              <td data-label="Catégorie">{{ categoryLabels[ticket.category] }}</td>
              <td data-label="Priorité"><span class="priority-badge" :class="`priority-badge-${ticket.priority.toLowerCase()}`">{{ priorityLabels[ticket.priority] }}</span></td>
              <td data-label="Statut"><span class="status-badge" :class="`status-${ticket.status.toLowerCase()}`">{{ statusLabels[ticket.status] }}</span></td>
              <td data-label="Mis à jour">{{ formatDate(ticket.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
