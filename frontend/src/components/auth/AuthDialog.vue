<script setup lang="ts">
import { nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import kakaoTalkIcon from '../../assets/images/illustrations/kakao-talk.png'
import { useToast } from '../../composables/useToast'
import { useAuthStore } from '../../stores/auth'
import { ApiError } from '../../api/http'
import { isValidPassword, PASSWORD_POLICY_MESSAGE } from '../../utils/password'

const auth = useAuthStore()
const { showToast } = useToast()
const isSubmitting = ref(false)
const dialogRef = ref<InstanceType<typeof globalThis.HTMLElement> | null>(null)
const signupForm = reactive({
  email: '',
  password: '',
})
const loginForm = reactive({ email: '', password: '' })
const signupErrors = reactive({ email: '', password: '' })
const loginErrors = reactive({ email: '', password: '' })

function restoreScroll() {
  globalThis.document.body.style.overflow = ''
}

function clearForms() {
  Object.assign(signupForm, {
    email: '',
    password: '',
  })
  Object.assign(loginForm, { email: '', password: '' })
  Object.assign(signupErrors, { email: '', password: '' })
  Object.assign(loginErrors, { email: '', password: '' })
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

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function validateSignup() {
  signupErrors.email = signupForm.email
    ? isValidEmail(signupForm.email)
      ? ''
      : '이메일 형식을 확인해주세요.'
    : '이메일을 입력해주세요.'
  signupErrors.password = signupForm.password
    ? isValidPassword(signupForm.password)
      ? ''
      : PASSWORD_POLICY_MESSAGE
    : '비밀번호를 입력해주세요.'
  return !Object.values(signupErrors).some(Boolean)
}

function validateLogin() {
  loginErrors.email = loginForm.email
    ? isValidEmail(loginForm.email)
      ? ''
      : '이메일 형식을 확인해주세요.'
    : '이메일을 입력해주세요.'
  loginErrors.password = loginForm.password
    ? loginForm.password.length >= 8
      ? ''
      : '비밀번호는 8자 이상 입력해주세요.'
    : '비밀번호를 입력해주세요.'

  return !Object.values(loginErrors).some(Boolean)
}

function handleKakaoLogin() {
  try {
    auth.startKakaoLogin()
  } catch (error) {
    showToast(
      error instanceof Error
        ? error.message
        : '카카오 로그인을 시작하지 못했어요.',
    )
  }
}

async function handleSignupSubmit() {
  if (!validateSignup() || isSubmitting.value) return
  isSubmitting.value = true
  try {
    await auth.signup(signupForm.email, signupForm.password)
    showToast('회원가입이 완료됐어요. 환영해요!')
    clearForms()
  } catch (error) {
    showToast(
      error instanceof ApiError
        ? error.message
        : '회원가입에 실패했어요. 잠시 후 다시 시도해 주세요.',
    )
  } finally {
    isSubmitting.value = false
  }
}

async function handleLoginSubmit() {
  if (!validateLogin() || isSubmitting.value) return
  isSubmitting.value = true
  try {
    await auth.login(loginForm.email, loginForm.password)
    showToast('로그인했어요.')
    clearForms()
  } catch (error) {
    showToast(
      error instanceof ApiError
        ? error.message
        : '로그인에 실패했어요. 이메일과 비밀번호를 확인해 주세요.',
    )
  } finally {
    isSubmitting.value = false
  }
}

watch(
  () => auth.isDialogOpen,
  async (isOpen) => {
    if (isOpen) {
      globalThis.document.body.style.overflow = 'hidden'
      globalThis.addEventListener('keydown', handleKeydown)
      await nextTick()
      dialogRef.value
        ?.querySelector<InstanceType<typeof globalThis.HTMLElement>>(
          '[data-auth-initial-focus]',
        )
        ?.focus()
      return
    }

    restoreScroll()
    globalThis.removeEventListener('keydown', handleKeydown)
  },
)

watch(
  () => auth.dialogScreen,
  () => clearForms(),
)

onBeforeUnmount(() => {
  restoreScroll()
  globalThis.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog-pop" appear>
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
            aria-label="인증 창 닫기"
            @click="closeDialog"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M6 6l12 12M18 6 6 18"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
              />
            </svg>
          </button>

          <template v-if="auth.dialogScreen === 'signup'">
            <h2 id="auth-dialog-title">반가워요!</h2>
            <p>눈으로 즐기는 게임을 시작해요.</p>
            <div class="auth-dialog__signup-panel">
              <form
                class="auth-dialog__form"
                novalidate
                @submit.prevent="handleSignupSubmit"
              >
                <label for="signup-email">이메일</label>
                <input
                  id="signup-email"
                  v-model="signupForm.email"
                  type="email"
                  autocomplete="email"
                  data-auth-initial-focus
                  :aria-invalid="Boolean(signupErrors.email)"
                  aria-describedby="signup-email-error"
                />
                <p
                  id="signup-email-error"
                  class="auth-dialog__error"
                  role="alert"
                >
                  {{ signupErrors.email }}
                </p>
                <label for="signup-password">비밀번호</label>
                <input
                  id="signup-password"
                  v-model="signupForm.password"
                  type="password"
                  autocomplete="new-password"
                  maxlength="16"
                  placeholder="8~16자, 영문+숫자, 공백 불가"
                  :aria-invalid="Boolean(signupErrors.password)"
                  aria-describedby="signup-password-error"
                />
                <p
                  id="signup-password-error"
                  class="auth-dialog__error"
                  role="alert"
                >
                  {{ signupErrors.password }}
                </p>
                <button
                  class="auth-dialog__submit"
                  type="submit"
                  :disabled="isSubmitting"
                >
                  회원가입
                </button>
              </form>
              <p class="auth-dialog__switch">
                이미 계정이 있나요?
                <button type="button" @click="auth.openLogin">로그인</button>
              </p>
            </div>
            <div class="auth-dialog__social-login">
              <span>소셜 계정으로 시작하기</span>
              <button
                type="button"
                aria-label="카카오로 시작하기"
                @click="handleKakaoLogin"
              >
                <span class="auth-dialog__social-kakao-icon" aria-hidden="true">
                  <img :src="kakaoTalkIcon" alt="" />
                </span>
                <span>카카오 계정으로 시작하기</span>
              </button>
            </div>
          </template>

          <template v-else>
            <h2 id="auth-dialog-title">로그인</h2>
            <p>다시 만나서 반가워요. 게임을 이어서 즐겨요.</p>
            <form
              class="auth-dialog__form"
              novalidate
              @submit.prevent="handleLoginSubmit"
            >
              <label for="login-email">이메일</label>
              <input
                id="login-email"
                v-model="loginForm.email"
                type="email"
                autocomplete="email"
                data-auth-initial-focus
                :aria-invalid="Boolean(loginErrors.email)"
                aria-describedby="login-email-error"
              />
              <p id="login-email-error" class="auth-dialog__error" role="alert">
                {{ loginErrors.email }}
              </p>
              <label for="login-password">비밀번호</label>
              <input
                id="login-password"
                v-model="loginForm.password"
                type="password"
                autocomplete="current-password"
                :aria-invalid="Boolean(loginErrors.password)"
                aria-describedby="login-password-error"
              />
              <p
                id="login-password-error"
                class="auth-dialog__error"
                role="alert"
              >
                {{ loginErrors.password }}
              </p>
              <button
                class="auth-dialog__submit"
                type="submit"
                :disabled="isSubmitting"
              >
                로그인
              </button>
            </form>
            <p class="auth-dialog__switch">
              처음이신가요?
              <button type="button" @click="auth.openSignup">회원가입</button>
            </p>
            <div class="auth-dialog__social-login">
              <span>소셜 계정으로 로그인</span>
              <button
                type="button"
                aria-label="카카오로 로그인"
                @click="handleKakaoLogin"
              >
                <span class="auth-dialog__social-kakao-icon" aria-hidden="true">
                  <img :src="kakaoTalkIcon" alt="" />
                </span>
                <span>카카오 계정으로 로그인</span>
              </button>
            </div>
          </template>
        </section>
      </div>
    </Transition>
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
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.auth-dialog__close svg {
  width: 18px;
  height: 18px;
}
.auth-dialog__close:hover {
  background: var(--color-blue-soft);
}
.auth-dialog h2 {
  margin: 10px 0 7px;
  color: var(--color-ink);
  font-size: 31px;
  letter-spacing: -0.02em;
}
.auth-dialog > p {
  margin: 0 0 25px;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.6;
  word-break: keep-all;
}
.auth-dialog__submit {
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
  transition:
    background-color var(--duration-fast) ease,
    transform var(--duration-fast) ease;
}
.auth-dialog__submit:hover {
  background: color-mix(in srgb, var(--color-accent-blue) 85%, black);
  transform: translateY(-2px);
}
.auth-dialog__social-login {
  display: grid;
  justify-items: center;
  gap: 14px;
  margin-top: 26px;
}
.auth-dialog__social-login > span {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  color: var(--color-muted);
  font-size: 12px;
}
.auth-dialog__social-login > span::before,
.auth-dialog__social-login > span::after {
  flex: 1;
  height: 1px;
  content: '';
  background: var(--color-line);
}
.auth-dialog__social-login button {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  width: 100%;
  gap: 8px;
  padding: 0;
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 800;
  background: transparent;
  cursor: pointer;
  transition:
    filter var(--duration-fast) ease,
    transform var(--duration-fast) ease;
}
.auth-dialog__social-login button:hover {
  filter: brightness(0.97);
  transform: translateY(-2px);
}
.auth-dialog__social-kakao-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  overflow: hidden;
  justify-self: end;
  border-radius: 50%;
  background: #fee500;
}
.auth-dialog__social-kakao-icon img {
  width: 61%;
  height: 61%;
  object-fit: contain;
}
.auth-dialog__form {
  display: grid;
  gap: 7px;
}
.auth-dialog__signup-panel {
  margin-top: 4px;
  padding: 20px;
  border: 1px solid var(--color-line);
  border-radius: 16px;
  background: var(--color-surface-soft);
}
.auth-dialog__signup-panel .auth-dialog__form input {
  background: #fff;
}
.auth-dialog__form label {
  color: var(--color-ink);
  font-size: 12px;
  font-weight: 800;
}
.auth-dialog__form input {
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-line);
  border-radius: 9px;
  color: var(--color-ink);
  background: #fff;
  transition: border-color var(--duration-fast) ease;
}
.auth-dialog__form input[aria-invalid='true'] {
  border-color: var(--color-danger, #c2455a);
}
.auth-dialog__error {
  min-height: 17px;
  margin: -3px 0 2px;
  color: var(--color-danger, #c2455a);
  font-size: 11px;
  line-height: 1.4;
}
.auth-dialog__submit {
  margin-top: 5px;
  color: #fff;
  background: var(--color-accent-blue);
}
.auth-dialog__switch {
  margin: 22px 0 0;
  color: var(--color-muted);
  font-size: 13px;
  text-align: center;
}
.auth-dialog__signup-panel .auth-dialog__switch {
  margin-bottom: 0;
}
.auth-dialog__switch button {
  padding: 2px 4px;
  color: var(--color-accent-blue);
  background: transparent;
  font-weight: 800;
  cursor: pointer;
  transition: color var(--duration-fast) ease;
}
.auth-dialog__switch button:hover {
  color: var(--color-primary-hover);
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
.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: opacity 200ms ease;
}
.dialog-pop-enter-from,
.dialog-pop-leave-to {
  opacity: 0;
}
.dialog-pop-enter-active .auth-dialog,
.dialog-pop-leave-active .auth-dialog {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}
.dialog-pop-enter-from .auth-dialog,
.dialog-pop-leave-to .auth-dialog {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
</style>
