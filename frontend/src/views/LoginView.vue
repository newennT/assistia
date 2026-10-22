<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/services/api'
import { login } from '@/services/auth'

const email = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const route = useRoute()
const router = useRouter()

async function submitLogin() {
  errorMessage.value = ''
  submitting.value = true
  try {
    await login(email.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect.startsWith('/') ? redirect : '/')
  } catch (error) {
    errorMessage.value = error instanceof ApiError
      ? error.status === 401 ? 'Adresse e-mail ou mot de passe incorrect.' : error.message
      : 'La connexion a échoué. Réessayez dans quelques instants.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <div class="login-brand"><span class="brand-mark">A</span><span>Assist<span class="brand-accent">IA</span></span></div>
    <section class="login-card">
      <h1>Se connecter</h1>
      <p class="login-intro">Connectez-vous à votre espace de support.</p>
      <p v-if="route.query.unavailable" class="form-error" role="alert">Le serveur AssistIA ne répond pas. Vérifiez qu’il est démarré puis réessayez.</p>
      <form @submit.prevent="submitLogin">
        <label for="email">Adresse e-mail</label>
        <input id="email" v-model="email" type="email" autocomplete="username" placeholder="nom@entreprise.fr" required :disabled="submitting" />
        <label for="password">Mot de passe</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" placeholder="Votre mot de passe" required :disabled="submitting" />
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button class="primary-button login-submit" type="submit" :disabled="submitting">
          <span v-if="submitting" class="spinner" aria-hidden="true"></span>{{ submitting ? 'Connexion…' : 'Se connecter' }}
        </button>
      </form>
    </section>
    <div class="login-footer">AssistIA · Portail Support informatique</div>
  </main>
</template>
