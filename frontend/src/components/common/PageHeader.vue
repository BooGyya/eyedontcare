<script setup lang="ts">
defineProps<{
  eyebrow?: string
  title: string
  description: string
}>()
</script>

<template>
  <header class="page-header">
    <h1>{{ title }}</h1>
    <p>{{ description }}</p>
  </header>
</template>

<style scoped>
.page-header {
  margin: 8px 0 28px;
  animation: page-fade-up 0.4s var(--ease-out) both;
}

.page-header h1 {
  margin: 5px 0 7px;
  color: var(--color-ink);
  font-size: clamp(29px, 3vw, 40px);
  line-height: 1.25;
  letter-spacing: -0.02em;
  word-break: keep-all;
}

.page-header p {
  margin: 0;
  color: var(--color-muted);
  font-size: 15px;
  word-break: keep-all;
}
</style>
