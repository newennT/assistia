<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>

<template>
  <div class="placeholder-card">
    <span class="placeholder-icon">◷</span>
    <div><span class="eyebrow">EN COURS DE DÉVELOPPEMENT</span><h2>{{ title }}</h2><p>{{ description }}</p></div>
  </div>
</template>
