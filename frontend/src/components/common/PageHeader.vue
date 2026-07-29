<script setup lang="ts">
withDefaults(
  defineProps<{
    eyebrow?: string
    title: string
    description: string
  }>(),
  {
    eyebrow: "EYE DON'T CARE",
  },
)
</script>

<template>
  <header class="page-header">
    <span class="page-header__eyebrow">{{ eyebrow }}</span>
    <h1>{{ title }}</h1>
    <p>{{ description }}</p>
  </header>
</template>

<style scoped>
.page-header {
  margin: 8px 0 28px;
}

.page-header__eyebrow {
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
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
