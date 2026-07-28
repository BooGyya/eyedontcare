<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<template>
  <section class="pending-page">
    <p>준비 중</p>
    <h1>{{ title }}</h1>
    <span>이 화면은 다음 전환 단계에서 구현할 예정이에요.</span>
  </section>
</template>

<style scoped>
.pending-page {
  display: grid;
  min-height: 55vh;
  place-content: center;
  gap: 8px;
  text-align: center;
}

.pending-page p {
  color: var(--color-accent-blue);
  font-size: 13px;
  font-weight: 800;
}

.pending-page h1 {
  font-size: 32px;
}

.pending-page span {
  color: var(--color-muted);
}
</style>
