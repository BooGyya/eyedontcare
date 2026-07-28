<script setup lang="ts">
import { nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import kakaoTalkIcon from '../../assets/images/illustrations/kakao-talk.png'
import { useToast } from '../../composables/useToast'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const { showToast } = useToast()
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
      : '\uC774\uBA54\uC77C \uD615\uC2DD\uC744 \uD655\uC778\uD574\uC8FC\uC138\uC694.'
    : '\uC774\uBA54\uC77C\uC744 \uC785\uB825\uD574\uC8FC\uC138\uC694.'
  signupErrors.password = signupForm.password
    ? signupForm.password.length >= 8
      ? ''
      : '\uBE44\uBC00\uBC88\uD638\uB294 8\uC790 \uC774\uC0C1 \uC785\uB825\uD574\uC8FC\uC138\uC694.'
    : '\uBE44\uBC00\uBC88\uD638\uB97C \uC785\uB825\uD574\uC8FC\uC138\uC694.'
  return !Object.values(signupErrors).some(Boolean)
}

function validateLogin() {
  loginErrors.email = loginForm.email
    ? isValidEmail(loginForm.email)
      ? ''
      : '\uC774\uBA54\uC77C \uD615\uC2DD\uC744 \uD655\uC778\uD574\uC8FC\uC138\uC694.'
    : '\uC774\uBA54\uC77C\uC744 \uC785\uB825\uD574\uC8FC\uC138\uC694.'
  loginErrors.password = loginForm.password
    ? loginForm.password.length >= 8
      ? ''
      : '\uBE44\uBC00\uBC88\uD638\uB294 8\uC790 \uC774\uC0C1 \uC785\uB825\uD574\uC8FC\uC138\uC694.'
    : '\uBE44\uBC00\uBC88\uD638\uB97C \uC785\uB825\uD574\uC8FC\uC138\uC694.'

  return !Object.values(loginErrors).some(Boolean)
}

function handleMockKakaoLogin() {
  auth.signInWithMockKakao()
  showToast(
    '\uCE74\uCE74\uC624 \uC5F0\uB3D9 \uC5C6\uC774 mock \uC778\uC99D \uC0C1\uD0DC\uB85C \uC804\uD658\uD588\uC5B4\uC694.',
  )
}

function handleSignupSubmit() {
  if (!validateSignup()) return
  const nickname = auth.registerMockUser()
  showToast(
    `${nickname}\uB2D8, mock \uAC00\uC785\uC73C\uB85C \uD658\uC601\uD574\uC694!`,
  )
  clearForms()
}

function handleLoginSubmit() {
  if (!validateLogin()) return
  auth.signInWithMockKakao()
  showToast(
    '\uC774\uBA54\uC77C \uC815\uBCF4\uB294 \uC800\uC7A5\uD558\uC9C0 \uC54A\uACE0 mock \uC778\uC99D\uC73C\uB85C \uC804\uD658\uD588\uC5B4\uC694.',
  )
  clearForms()
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
          aria-label="&#xC778;&#xC99D; &#xCC3D; &#xB2EB;&#xAE30;"
          @click="closeDialog"
        >
          &#xD7;
        </button>

        <template v-if="auth.dialogScreen === 'signup'">
          <h2 id="auth-dialog-title">&#xBC18;&#xAC00;&#xC6CC;&#xC694;!</h2>
          <p>
            &#xB208;&#xC73C;&#xB85C; &#xC990;&#xAE30;&#xB294;
            &#xAC8C;&#xC784;&#xC744; &#xC2DC;&#xC791;&#xD574;&#xC694;.
          </p>
          <div class="auth-dialog__signup-panel">
            <form
              class="auth-dialog__form"
              novalidate
              @submit.prevent="handleSignupSubmit"
            >
              <label for="signup-email">&#xC774;&#xBA54;&#xC77C;</label>
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
              <label for="signup-password"
                >&#xBE44;&#xBC00;&#xBC88;&#xD638;</label
              >
              <input
                id="signup-password"
                v-model="signupForm.password"
                type="password"
                autocomplete="new-password"
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
              <button class="auth-dialog__submit" type="submit">
                &#xD68C;&#xC6D0;&#xAC00;&#xC785;
              </button>
            </form>
            <p class="auth-dialog__switch">
              &#xC774;&#xBBF8; &#xACC4;&#xC815;&#xC774;
              &#xC788;&#xB098;&#xC694;?
              <button type="button" @click="auth.openLogin">
                &#xB85C;&#xADF8;&#xC778;
              </button>
            </p>
          </div>
          <div class="auth-dialog__social-login">
            <span
              >&#xC18C;&#xC15C; &#xACC4;&#xC815;&#xC73C;&#xB85C;
              &#xC2DC;&#xC791;&#xD558;&#xAE30;</span
            >
            <button
              type="button"
              aria-label="&#xCE74;&#xCE74;&#xC624;&#xB85C; &#xC2DC;&#xC791;&#xD558;&#xAE30;"
              @click="handleMockKakaoLogin"
            >
              <span class="auth-dialog__social-kakao-icon" aria-hidden="true">
                <img :src="kakaoTalkIcon" alt="" />
              </span>
              <span
                >&#xCE74;&#xCE74;&#xC624; &#xACC4;&#xC815;&#xC73C;&#xB85C;
                &#xC2DC;&#xC791;&#xD558;&#xAE30;</span
              >
            </button>
          </div>
        </template>

        <template v-else>
          <h2 id="auth-dialog-title">&#xB85C;&#xADF8;&#xC778;</h2>
          <p>
            &#xB2E4;&#xC2DC; &#xB9CC;&#xB098;&#xC11C;
            &#xBC18;&#xAC00;&#xC6CC;&#xC694;. &#xAC8C;&#xC784;&#xC744;
            &#xC774;&#xC5B4;&#xC11C; &#xC990;&#xACA8;&#xC694;.
          </p>
          <form
            class="auth-dialog__form"
            novalidate
            @submit.prevent="handleLoginSubmit"
          >
            <label for="login-email">&#xC774;&#xBA54;&#xC77C;</label>
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
            <label for="login-password">&#xBE44;&#xBC00;&#xBC88;&#xD638;</label>
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
            <button class="auth-dialog__submit" type="submit">
              &#xB85C;&#xADF8;&#xC778;
            </button>
          </form>
          <p class="auth-dialog__switch">
            &#xCC98;&#xC74C;&#xC774;&#xC2E0;&#xAC00;&#xC694;?
            <button type="button" @click="auth.openSignup">
              &#xD68C;&#xC6D0;&#xAC00;&#xC785;
            </button>
          </p>
          <div class="auth-dialog__social-login">
            <span
              >&#xC18C;&#xC15C; &#xACC4;&#xC815;&#xC73C;&#xB85C;
              &#xB85C;&#xADF8;&#xC778;</span
            >
            <button
              type="button"
              aria-label="&#xCE74;&#xCE74;&#xC624;&#xB85C; &#xB85C;&#xADF8;&#xC778;"
              @click="handleMockKakaoLogin"
            >
              <span class="auth-dialog__social-kakao-icon" aria-hidden="true">
                <img :src="kakaoTalkIcon" alt="" />
              </span>
              <span
                >&#xCE74;&#xCE74;&#xC624; &#xACC4;&#xC815;&#xC73C;&#xB85C;
                &#xB85C;&#xADF8;&#xC778;</span
              >
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
}
.auth-dialog__form input[aria-invalid='true'] {
  border-color: #c44a55;
}
.auth-dialog__error {
  min-height: 17px;
  margin: -3px 0 2px;
  color: #b63745;
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
