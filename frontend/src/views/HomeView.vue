<script setup lang="ts">
import { authState } from '@/services/auth'
</script>

<template>
  <div class="welcome-panel">
    <div class="welcome-copy"><span class="eyebrow">ESPACE SUPPORT</span><h2>Bonjour {{ authState.user?.firstName }},</h2><p>Bienvenue dans AssistIA, votre espace de support informatique.</p></div>
    <div class="welcome-orb" aria-hidden="true">A</div>
  </div>
  <div class="section-heading"><div><h2>Votre espace de travail</h2><p>Les indicateurs et le suivi des tickets seront ajoutés à la prochaine étape.</p></div></div>
  <div class="feature-grid">
    <article class="feature-card"><span class="feature-symbol blue">▤</span><div><h3>Gestion des tickets</h3><p>Consultez les demandes qui vous concernent.</p><RouterLink to="/tickets">Accéder aux tickets <span>→</span></RouterLink></div></article>
    <article class="feature-card"><span class="feature-symbol green">＋</span><div><h3>Nouvelle demande</h3><p>Signalez un incident à l’équipe informatique.</p><RouterLink to="/tickets/new">Créer un ticket <span>→</span></RouterLink></div></article>
  </div>
  <div class="notice-bar"><span class="notice-icon">i</span><span>Le tableau de bord des tickets sera activé après la mise en place des écrans de gestion.</span></div>
</template>
