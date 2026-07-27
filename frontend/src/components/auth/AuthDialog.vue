<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useToast } from '../../composables/useToast'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const { showToast } = useToast()
const dialogRef = ref<any>()

function restoreScroll() {
  globalThis.document.body.style.overflow = ''
}

function closeDialog() {
  auth.closeDialog()
}

function handleKeydown(event: { key: string }) {
  if (event.key === 'Escape') closeDialog()
}

function handleBackdropClick(event: {
  target: unknown
  currentTarget: unknown
}) {
  if (event.target === event.currentTarget) closeDialog()
}

function handleMockKakaoLogin() {
  auth.signInWithMockKakao()
  showToast('카카오 로그인은 현재 mock 인증으로 전환됐어요.')
}

function handleGuestContinue() {
  auth.continueAsGuest()
  showToast('게스트 모드로 시작했어요. 기록은 이번 접속에서만 유지돼요.')
}

watch(
  () => auth.isDialogOpen,
  async (isOpen) => {
    if (isOpen) {
      globalThis.document.body.style.overflow = 'hidden'
      globalThis.addEventListener('keydown', handleKeydown)
      await nextTick()
      dialogRef.value?.querySelector('[data-auth-initial-focus]')?.focus()
      return
    }

    restoreScroll()
    globalThis.removeEventListener('keydown', handleKeydown)
  },
)

onBeforeUnmount(() => {
  restoreScroll()
  globalThis.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="auth.isDialogOpen"
      class="auth-dialog-backdrop"
      @click="handleBackdropClick"
    >
      <section
        ref="dialogRef"
        class="auth-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="auth-dialog-title"
      >
        <button
          class="auth-dialog__close"
          type="button"
          aria-label="로그인 창 닫기"
          @click="closeDialog"
        >
          ×
        </button>

        <template v-if="auth.dialogScreen === 'login'">
          <span class="auth-dialog__eyebrow">WELCOME TO EYE DON'T CARE</span>
          <h2 id="auth-dialog-title">반가워요!</h2>
          <p>로그인하고 눈으로 즐기는 게임을 시작해요.</p>
          <button
            class="auth-dialog__provider auth-dialog__provider--kakao"
            type="button"
            data-auth-initial-focus
            @click="handleMockKakaoLogin"
          >
            <span aria-hidden="true">K</span>카카오로 시작하기
          </button>
          <div class="auth-dialog__divider"><span>또는</span></div>
          <button
            class="auth-dialog__provider auth-dialog__provider--guest"
            type="button"
            @click="auth.openGuestGuide"
          >
            <span aria-hidden="true">●</span>게스트로 시작하기
          </button>
          <aside class="auth-dialog__note">
            <strong>게스트 모드 안내</strong>
            <ul>
              <li>모든 게임을 바로 즐길 수 있어요.</li>
              <li>소모임은 이용할 수 없어요.</li>
              <li>기록은 재접속 시 누적되지 않아요.</li>
            </ul>
          </aside>
          <button
            class="auth-dialog__existing"
            type="button"
            @click="handleMockKakaoLogin"
          >
            기존 사용자라면 바로 시작하기 →
          </button>
        </template>

        <template v-else>
          <span class="auth-dialog__guest-badge">게스트 모드</span>
          <h2 id="auth-dialog-title">게스트로 시작할까요?</h2>
          <p>게임은 바로 즐길 수 있지만 일부 기능과 기록 저장이 제한돼요.</p>
          <aside class="auth-dialog__note">
            <strong>게스트 모드에서 가능한 것</strong>
            <ul>
              <li>5가지 게임을 자유롭게 플레이할 수 있어요.</li>
              <li>이번 접속 동안의 점수는 확인할 수 있어요.</li>
              <li>소모임은 카카오 로그인 후 이용할 수 있어요.</li>
              <li>재접속하면 게임 기록은 초기화돼요.</li>
            </ul>
          </aside>
          <div class="auth-dialog__actions">
            <button
              class="auth-dialog__continue"
              type="button"
              data-auth-initial-focus
              @click="handleGuestContinue"
            >
              게스트로 계속하기
            </button>
            <button
              class="auth-dialog__back"
              type="button"
              @click="auth.openLogin"
            >
              카카오로 로그인하기
            </button>
          </div>
        </template>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.auth-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(23, 36, 61, 0.5);
}
.auth-dialog {
  position: relative;
  width: min(100%, 480px);
  max-height: calc(100vh - 48px);
  overflow: auto;
  padding: 42px;
  border: 1px solid var(--color-line);
  border-radius: 24px;
  background: #fff;
  box-shadow: var(--shadow-float);
}
.auth-dialog__close {
  position: absolute;
  top: 16px;
  right: 16px;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}
.auth-dialog__eyebrow,
.auth-dialog__guest-badge {
  display: inline-block;
  color: var(--color-accent-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.auth-dialog__guest-badge {
  padding: 6px 10px;
  border-radius: var(--radius-button);
  color: #5771ba;
  background: var(--color-blue-soft);
  letter-spacing: 0;
}
.auth-dialog h2 {
  margin: 10px 0 7px;
  color: var(--color-ink);
  font-size: 31px;
  letter-spacing: -0.05em;
}
.auth-dialog > p {
  margin: 0 0 25px;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.6;
  word-break: keep-all;
}
.auth-dialog__provider {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 14px;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}
.auth-dialog__provider--kakao {
  color: #342d18;
  background: #fee500;
}
.auth-dialog__provider--guest {
  border: 1px solid var(--color-line);
  color: var(--color-ink);
  background: #fff;
}
.auth-dialog__provider:hover,
.auth-dialog__continue:hover {
  filter: brightness(0.97);
  transform: translateY(-1px);
}
.auth-dialog__divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 17px 0;
  color: var(--color-muted);
  font-size: 12px;
}
.auth-dialog__divider::before,
.auth-dialog__divider::after {
  flex: 1;
  height: 1px;
  content: '';
  background: var(--color-line);
}
.auth-dialog__note {
  margin: 21px 0;
  padding: 15px 17px;
  border-radius: 13px;
  background: var(--color-surface-soft);
}
.auth-dialog__note strong {
  font-size: 13px;
}
.auth-dialog__note ul {
  display: grid;
  gap: 5px;
  margin: 10px 0 0;
  padding-left: 18px;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.5;
}
.auth-dialog__existing {
  display: block;
  width: 100%;
  color: var(--color-accent-blue);
  background: transparent;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}
.auth-dialog__actions {
  display: grid;
  gap: 9px;
}
.auth-dialog__continue,
.auth-dialog__back {
  width: 100%;
  padding: 13px;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}
.auth-dialog__continue {
  color: #fff;
  background: var(--color-accent-blue);
}
.auth-dialog__back {
  border: 1px solid var(--color-line);
  color: var(--color-ink);
  background: #fff;
}
@media (max-width: 640px) {
  .auth-dialog-backdrop {
    padding: 16px;
  }
  .auth-dialog {
    max-height: calc(100vh - 32px);
    padding: 36px 22px 25px;
  }
  .auth-dialog h2 {
    font-size: 27px;
  }
}
</style>
