<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isApiErrorStatus } from '@/services/api'
import { clearSession } from '@/services/auth'
import { createTicket } from '@/services/tickets'
import type { TicketCategory } from '@/types/ticket'

const router = useRouter()
const route = useRoute()
const form = reactive({ title: '', description: '', category: '' as TicketCategory | '' })
const errors = reactive<{ title: string; description: string; category: string }>({ title: '', description: '', category: '' })
const submitting = ref(false)
const requestError = ref('')
const categoryOptions: { value: TicketCategory; label: string }[] = [
  { value: 'WINDOWS', label: 'Windows' },
  { value: 'LINUX', label: 'Linux' },
  { value: 'DNS', label: 'DNS' },
  { value: 'DHCP', label: 'DHCP' },
  { value: 'VPN', label: 'VPN' },
  { value: 'NETWORK', label: 'Réseau' },
  { value: 'PRINTER', label: 'Imprimante' },
  { value: 'USER_ACCOUNT', label: 'Compte utilisateur' },
  { value: 'PERMISSIONS', label: 'Autorisations' },
  { value: 'M365', label: 'Microsoft 365' },
  { value: 'BUSINESS_APP', label: 'Application métier' },
  { value: 'GENERAL', label: 'Général' },
  { value: 'OTHER', label: 'Autre' },
]
const hasErrors = computed(() => Object.values(errors).some(Boolean))

function validateForm(): boolean {
  errors.title = form.title.trim() ? '' : 'Saisissez un titre.'
  if (form.title.trim().length > 255) errors.title = 'Le titre ne peut pas dépasser 255 caractères.'
  errors.description = form.description.trim() ? '' : 'Décrivez le problème rencontré.'
  errors.category = form.category ? '' : 'Sélectionnez une catégorie.'
  return !hasErrors.value
}

async function submitForm() {
  requestError.value = ''
  if (!validateForm() || submitting.value) return

  submitting.value = true
  try {
    const ticket = await createTicket({
      title: form.title.trim(),
      description: form.description.trim(),
      category: form.category as TicketCategory,
    })
    await router.push({ name: 'ticket-detail', params: { id: ticket.id } })
  } catch (error) {
    if (isApiErrorStatus(error, 401)) {
      clearSession()
      await router.replace({ name: 'login', query: { redirect: route.fullPath, expired: 'true' } })
      return
    }
    requestError.value = error instanceof Error
      ? error.message
      : 'La création du ticket a échoué. Vérifiez votre connexion puis réessayez.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="ticket-create-page">
    <RouterLink class="back-link" to="/tickets">← Retour aux tickets</RouterLink>
    <div class="page-intro">
      <div><span class="eyebrow">NOUVELLE DEMANDE</span><h2>Créer un ticket</h2><p>Décrivez votre demande pour que l’équipe informatique puisse vous aider.</p></div>
    </div>

    <form class="panel ticket-form" novalidate @submit.prevent="submitForm">
      <div class="form-section-heading"><h3>Informations sur l’incident</h3><p>Les champs marqués d’un astérisque sont obligatoires.</p></div>
      <div class="form-field">
        <label for="ticket-title">Titre <span aria-hidden="true">*</span></label>
        <input
          id="ticket-title"
          v-model="form.title"
          type="text"
          maxlength="255"
          autocomplete="off"
          placeholder="Ex. Impossible de se connecter au VPN"
          :aria-invalid="Boolean(errors.title)"
          :aria-describedby="errors.title ? 'ticket-title-help ticket-title-error' : 'ticket-title-help'"
          :disabled="submitting"
          @input="errors.title = ''"
        />
        <div class="field-help-row"><small id="ticket-title-help">Résumez le problème en quelques mots.</small><small>{{ form.title.length }}/255</small></div>
        <p v-if="errors.title" id="ticket-title-error" class="field-error" role="alert">{{ errors.title }}</p>
      </div>

      <div class="form-field">
        <label for="ticket-category">Catégorie <span aria-hidden="true">*</span></label>
        <select
          id="ticket-category"
          v-model="form.category"
          :aria-invalid="Boolean(errors.category)"
          :aria-describedby="errors.category ? 'ticket-category-help ticket-category-error' : 'ticket-category-help'"
          :disabled="submitting"
          @change="errors.category = ''"
        >
          <option value="" disabled>Sélectionnez une catégorie</option>
          <option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <small id="ticket-category-help">Choisissez le domaine qui correspond le mieux à votre demande.</small>
        <p v-if="errors.category" id="ticket-category-error" class="field-error" role="alert">{{ errors.category }}</p>
      </div>

      <div class="form-field">
        <label for="ticket-description">Description <span aria-hidden="true">*</span></label>
        <textarea
          id="ticket-description"
          v-model="form.description"
          rows="7"
          placeholder="Indiquez les étapes suivies, le message d’erreur éventuel et le résultat attendu…"
          :aria-invalid="Boolean(errors.description)"
          :aria-describedby="errors.description ? 'ticket-description-help ticket-description-error' : 'ticket-description-help'"
          :disabled="submitting"
          @input="errors.description = ''"
        ></textarea>
        <small id="ticket-description-help">Plus votre description est précise, plus l’équipe pourra vous répondre efficacement.</small>
        <p v-if="errors.description" id="ticket-description-error" class="field-error" role="alert">{{ errors.description }}</p>
      </div>

      <p class="form-notice">Le statut et la priorité initiale sont définis par les règles d’accès de votre compte.</p>
      <p v-if="requestError" class="form-error submit-error" role="alert">{{ requestError }}</p>
      <div class="form-actions">
        <RouterLink class="secondary-button cancel-button" to="/tickets">Annuler</RouterLink>
        <button class="primary-button" type="submit" :disabled="submitting">
          <span v-if="submitting" class="spinner" aria-hidden="true"></span>
          {{ submitting ? 'Création…' : 'Créer le ticket' }}
        </button>
      </div>
    </form>
  </div>
</template>
