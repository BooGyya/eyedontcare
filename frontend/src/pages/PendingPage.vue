<script setup lang="ts">
import mascotEyeImage from '../assets/images/brand/mascot-eye.png'

defineProps<{
  title: string
}>()
</script>

<template>
  <section class="pending-page">
    <img
      class="pending-page__mascot"
      :src="mascotEyeImage"
      alt=""
      aria-hidden="true"
    />
    <p>준비 중</p>
    <h1>{{ title }}</h1>
    <span>이 화면은 다음 전환 단계에서 구현할 예정이에요.</span>
    <div class="pending-page__actions">
      <RouterLink class="pending-page__cta" to="/">홈으로 가기</RouterLink>
      <RouterLink class="pending-page__link" to="/games"
        >게임 보러 가기</RouterLink
      >
    </div>
  </section>
</template>

<style scoped>
.pending-page {
  display: grid;
  min-height: 55vh;
  place-content: center;
  justify-items: center;
  gap: 8px;
  text-align: center;
  animation: page-fade-up 0.4s var(--ease-out);
}

.pending-page__mascot {
  width: 120px;
  height: 120px;
  margin-bottom: 6px;
  object-fit: contain;
  animation: pending-page-drift 5.5s ease-in-out infinite;
}

@keyframes pending-page-drift {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
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

.pending-page__actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
}

.pending-page__cta {
  display: inline-flex;
  align-items: center;
  min-height: 42px;
  padding: 0 24px;
  border-radius: var(--radius-button);
  color: #fff;
  background: var(--color-primary);
  font-weight: 800;
  text-decoration: none;
  transition: background-color var(--duration-fast) ease;
}

.pending-page__cta:hover {
  background: var(--color-primary-hover);
}

.pending-page__link {
  color: var(--color-accent-blue);
  font-weight: 700;
  text-decoration: none;
  transition: color var(--duration-fast) ease;
}

.pending-page__link:hover {
  color: var(--color-primary-hover);
}
</style>
