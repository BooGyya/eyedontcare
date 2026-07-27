<script setup lang="ts">
import PageHeader from '../components/common/PageHeader.vue'
import { useAuthStore } from '../stores/auth'

defineProps<{
  title: string
  description: string
  items: readonly string[]
}>()

const auth = useAuthStore()
</script>

<template>
  <section class="account-page">
    <PageHeader :title="title" :description="description" />
    <section
      v-if="!auth.isAuthenticated"
      class="account-page__access-notice"
      aria-live="polite"
    >
      <strong
        >{{ auth.isGuest ? '게스트 모드에서는' : '로그인 후에는' }} 이 기능을
        이용할 수 있어요.</strong
      >
      <p>로그인하면 활동 기록과 개인 설정을 확인할 수 있습니다.</p>
      <button type="button" @click="auth.openLogin">로그인하기</button>
    </section>
    <div v-else class="account-page__panel">
      <section v-for="item in items" :key="item">
        <h2>{{ item }}</h2>
        <p>이 기능의 상세 설정은 준비 중입니다.</p>
        <button disabled type="button">준비 중</button>
      </section>
    </div>
  </section>
</template>

<style scoped>
.account-page {
  padding: 32px 0 54px;
}
.account-page__panel {
  display: grid;
  gap: 12px;
  width: min(100%, 720px);
}
.account-page__panel section {
  display: flex;
  align-items: center;
  gap: 17px;
  padding: 19px 22px;
  border: 1px solid var(--color-line);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.account-page h2 {
  flex: 0 0 140px;
  margin: 0;
  font-size: 17px;
}
.account-page p {
  margin: 0;
  color: var(--color-muted);
  font-size: 13px;
}
.account-page button {
  margin-left: auto;
  padding: 8px 12px;
  border-radius: 8px;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  font-size: 12px;
}
.account-page__access-notice {
  display: grid;
  justify-items: start;
  gap: 9px;
  width: min(100%, 620px);
  padding: 25px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface-soft);
}
.account-page__access-notice strong {
  font-size: 18px;
}
.account-page__access-notice p {
  line-height: 1.5;
}
.account-page__access-notice button {
  margin: 4px 0 0;
  color: #fff;
  background: var(--color-accent-blue);
  font-weight: 800;
  cursor: pointer;
}
@media (max-width: 640px) {
  .account-page {
    padding-top: 24px;
  }
  .account-page__panel section {
    flex-wrap: wrap;
  }
  .account-page h2 {
    flex-basis: auto;
  }
}
</style>
