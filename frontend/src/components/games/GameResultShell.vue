<script setup lang="ts">
defineProps<{
  title: string
  modeLabel: string
  headline: string
  summary: string
  hideOutcomeIntro?: boolean
  hideHeader?: boolean
  hideTitle?: boolean
}>()
</script>

<template>
  <section
    class="result-shell"
    :class="{ 'result-shell--compact': hideOutcomeIntro }"
  >
    <template v-if="!hideHeader">
      <span>{{ modeLabel }}</span>
      <h1 v-if="!hideTitle">{{ title }} 결과</h1>
    </template>
    <template v-if="!hideOutcomeIntro && !hideHeader">
      <h2>{{ headline }}</h2>
      <p>{{ summary }}</p>
    </template>
    <slot />
  </section>
</template>

<style scoped>
.result-shell {
  width: min(100%, 900px);
  margin: 0 auto;
  position: relative;
  padding: 34px 0 52px;
  text-align: center;
}
.result-shell > span {
  display: inline-block;
  padding: 5px 11px;
  border: 0;
  border-radius: 999px;
  color: var(--color-accent-blue);
  background: #f1f2ff;
  font-size: 12px;
  font-weight: 800;
}
.result-shell h1 {
  margin: 6px 0 14px;
  color: var(--color-muted);
  font-family: inherit;
  font-size: 16px;
}
.result-shell h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(28px, 4vw, 42px);
  font-family: inherit;
  letter-spacing: -0.06em;
  transform: none;
}
.result-shell > p {
  margin: 10px 0 24px;
  color: var(--color-muted);
}
.result-shell--compact {
  padding-top: 18px;
}
.result-shell--compact h1 {
  margin: 6px 0 10px;
}
</style>
