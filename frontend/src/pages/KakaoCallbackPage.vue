<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/http'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const state = ref<'loading' | 'error'>('loading')
const message = ref('카카오 로그인 중이에요...')

onMounted(async () => {
  const code = String(route.query.code ?? '')
  if (!code) {
    state.value = 'error'
    message.value = '카카오 인가 코드가 없어 로그인하지 못했어요.'
    return
  }
  try {
    await auth.loginWithKakao(code)
    await router.replace({ name: 'home' })
  } catch (error) {
    state.value = 'error'
    message.value =
      error instanceof ApiError
        ? error.message
        : '카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.'
  }
})
</script>

<template>
  <section class="kakao-callback">
    <p v-if="state === 'loading'" class="kakao-callback__loading">
      <i aria-hidden="true" />{{ message }}
    </p>
    <template v-else>
      <h1>로그인에 실패했어요</h1>
      <p>{{ message }}</p>
      <RouterLink class="kakao-callback__home" to="/">홈으로 돌아가기</RouterLink>
    </template>
  </section>
</template>

<style scoped>
.kakao-callback {
  display: grid;
  place-items: center;
  gap: 14px;
  padding: 88px 20px;
  text-align: center;
}
.kakao-callback h1 {
  margin: 0;
  color: var(--color-ink);
}
.kakao-callback p {
  margin: 0;
  color: var(--color-muted);
}
.kakao-callback__loading {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.kakao-callback__loading i {
  width: 20px;
  height: 20px;
  border: 3px solid var(--color-blue-soft);
  border-top-color: var(--color-accent-blue);
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}
.kakao-callback__home {
  color: var(--color-accent-blue);
  font-weight: 800;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
