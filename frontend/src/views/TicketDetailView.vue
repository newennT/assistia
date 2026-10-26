<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isApiErrorStatus } from '@/services/api'
import { clearSession, authState } from '@/services/auth'
import { changeTicketPriority, changeTicketStatus, getTicket, updateTicket } from '@/services/tickets'
import type { Ticket, TicketCategory, TicketPriority, TicketStatus } from '@/types/ticket'

const route = useRoute()
const router = useRouter()
const ticket = ref<Ticket | null>(null)
const loading = ref(true)
const error = ref('')
const editing = ref(false)
const saving = ref(false)
const changing = ref(false)
const requestError = ref('')
const fieldError = ref('')
const form = reactive({ title: '', description: '', category: '' as TicketCategory | '' })
const isStaff = computed(() => authState.user?.role === 'TECHNICIAN' || authState.user?.role === 'ADMIN')
const canEditTicket = computed(() => isStaff.value || authState.user?.id === ticket.value?.creatorId)
const canEditCategory = computed(() => isStaff.value)
const categoryOptions: { value: TicketCategory; label: string }[] = [
  { value: 'WINDOWS', label: 'Windows' }, { value: 'LINUX', label: 'Linux' }, { value: 'DNS', label: 'DNS' },
  { value: 'DHCP', label: 'DHCP' }, { value: 'VPN', label: 'VPN' }, { value: 'NETWORK', label: 'Réseau' },
  { value: 'PRINTER', label: 'Imprimante' }, { value: 'USER_ACCOUNT', label: 'Compte utilisateur' },
  { value: 'PERMISSIONS', label: 'Autorisations' }, { value: 'M365', label: 'Microsoft 365' },
  { value: 'BUSINESS_APP', label: 'Application métier' }, { value: 'GENERAL', label: 'Général' }, { value: 'OTHER', label: 'Autre' },
]
const categoryLabels: Record<TicketCategory, string> = Object.fromEntries(categoryOptions.map(({ value, label }) => [value, label])) as Record<TicketCategory, string>
const statusOptions: { value: TicketStatus; label: string }[] = [
  { value: 'OPEN', label: 'Ouvert' }, { value: 'IN_PROGRESS', label: 'En cours' }, { value: 'PENDING', label: 'En attente' },
  { value: 'RESOLVED', label: 'Résolu' }, { value: 'CLOSED', label: 'Fermé' },
]
const priorityOptions: { value: TicketPriority; label: string }[] = [
  { value: 'LOW', label: 'Basse' }, { value: 'MEDIUM', label: 'Moyenne' }, { value: 'HIGH', label: 'Haute' }, { value: 'CRITICAL', label: 'Critique' },
]
const statusLabels = Object.fromEntries(statusOptions.map(({ value, label }) => [value, label])) as Record<TicketStatus, string>
const priorityLabels = Object.fromEntries(priorityOptions.map(({ value, label }) => [value, label])) as Record<TicketPriority, string>

function formatDate(value: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' }).format(new Date(value))
}

function copyTicketToForm(value: Ticket) {
  form.title = value.title
  form.description = value.description
  form.category = value.category
}

async function loadTicket() {
  loading.value = true
  error.value = ''
  ticket.value = null
  const id = Number(route.params.id)
  try {
    ticket.value = await getTicket(id)
    copyTicketToForm(ticket.value)
  } catch (cause) {
    if (isApiErrorStatus(cause, 401)) {
      clearSession()
      await router.replace({ name: 'login', query: { redirect: route.fullPath, expired: 'true' } })
      return
    }
    error.value = isApiErrorStatus(cause, 404)
      ? 'Ce ticket est introuvable ou n’est pas accessible à votre compte.'
      : isApiErrorStatus(cause, 403) ? 'Vous n’avez pas accès à ce ticket.'
        : cause instanceof Error ? cause.message : 'Impossible de charger ce ticket. Réessayez.'
  } finally {
    loading.value = false
  }
}

function startEditing() {
  if (!ticket.value) return
  copyTicketToForm(ticket.value)
  requestError.value = ''
  fieldError.value = ''
  editing.value = true
}

function cancelEditing() {
  if (ticket.value) copyTicketToForm(ticket.value)
  editing.value = false
  requestError.value = ''
  fieldError.value = ''
}

async function saveTicket() {
  if (!ticket.value || saving.value) return
  fieldError.value = ''
  requestError.value = ''
  if (!form.title.trim()) fieldError.value = 'Saisissez un titre.'
  else if (form.title.trim().length > 255) fieldError.value = 'Le titre ne peut pas dépasser 255 caractères.'
  else if (!form.description.trim()) fieldError.value = 'Saisissez une description.'
  if (fieldError.value) return

  saving.value = true
  try {
    ticket.value = await updateTicket(ticket.value.id, {
      title: form.title.trim(), description: form.description.trim(),
      category: canEditCategory.value ? form.category as TicketCategory : ticket.value.category,
    })
    editing.value = false
  } catch (cause) {
    if (isApiErrorStatus(cause, 401)) {
      clearSession()
      await router.replace({ name: 'login', query: { redirect: route.fullPath, expired: 'true' } })
      return
    }
    requestError.value = cause instanceof Error ? cause.message : 'La modification a échoué. Réessayez.'
  } finally {
    saving.value = false
  }
}

async function updateClassification(kind: 'status' | 'priority', value: string) {
  if (!ticket.value || changing.value) return
  changing.value = true
  requestError.value = ''
  try {
    ticket.value = kind === 'status'
      ? await changeTicketStatus(ticket.value.id, value as TicketStatus)
      : await changeTicketPriority(ticket.value.id, value as TicketPriority)
  } catch (cause) {
    if (isApiErrorStatus(cause, 401)) {
      clearSession()
      await router.replace({ name: 'login', query: { redirect: route.fullPath, expired: 'true' } })
      return
    }
    requestError.value = cause instanceof Error ? cause.message : 'La modification a échoué. Réessayez.'
  } finally {
    changing.value = false
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
        <div><span class="eyebrow">TICKET #{{ ticket.id }}</span><h2>{{ editing ? 'Modifier le ticket' : ticket.title }}</h2></div>
        <span v-if="!isStaff" class="status-badge" :class="`status-${ticket.status.toLowerCase()}`">{{ statusLabels[ticket.status] }}</span>
      </div>
      <p v-if="requestError" class="form-error" role="alert">{{ requestError }}</p>
      <form v-if="editing" class="ticket-edit-form" novalidate @submit.prevent="saveTicket">
        <div class="form-field"><label for="edit-title">Titre</label><input id="edit-title" v-model="form.title" maxlength="255" :disabled="saving" /></div>
        <div class="form-field"><label for="edit-category">Catégorie</label>
          <select id="edit-category" v-model="form.category" :disabled="saving || !canEditCategory">
            <option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <small v-if="!canEditCategory">La catégorie est modifiable par un technicien ou un administrateur.</small>
        </div>
        <div class="form-field"><label for="edit-description">Description</label><textarea id="edit-description" v-model="form.description" rows="7" :disabled="saving"></textarea></div>
        <p v-if="fieldError" class="field-error" role="alert">{{ fieldError }}</p>
        <div class="form-actions"><button class="secondary-button cancel-button" type="button" :disabled="saving" @click="cancelEditing">Annuler</button>
          <button class="primary-button" type="submit" :disabled="saving">{{ saving ? 'Enregistrement…' : 'Enregistrer' }}</button></div>
      </form>
      <template v-else>
        <div class="detail-meta">
          <div><span>Catégorie</span><strong>{{ categoryLabels[ticket.category] }}</strong></div>
          <div><span>Priorité</span><strong><span class="priority-badge" :class="`priority-badge-${ticket.priority.toLowerCase()}`">{{ priorityLabels[ticket.priority] }}</span></strong></div>
          <div><span>Créé le</span><strong>{{ formatDate(ticket.createdAt) }}</strong></div>
          <div><span>Mis à jour</span><strong>{{ formatDate(ticket.updatedAt) }}</strong></div>
          <div v-if="isStaff" class="form-field"><label for="ticket-status">Statut</label><select id="ticket-status" :value="ticket.status" :disabled="changing" @change="updateClassification('status', ($event.target as HTMLSelectElement).value)"><option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></div>
          <div v-if="isStaff" class="form-field"><label for="ticket-priority">Priorité</label><select id="ticket-priority" :value="ticket.priority" :disabled="changing" @change="updateClassification('priority', ($event.target as HTMLSelectElement).value)"><option v-for="option in priorityOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></div>
        </div>
        <section class="description-section"><h3>Description</h3><p>{{ ticket.description }}</p></section>
        <div v-if="canEditTicket" class="form-actions"><button class="secondary-button cancel-button" type="button" @click="startEditing">Modifier les informations</button></div>
      </template>
    </article>
  </div>
</template>
