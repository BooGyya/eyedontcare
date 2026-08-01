import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { generateMockNickname, mockAuthenticatedUser } from '../mocks/auth'
import type {
  AuthDialogScreen,
  AuthStatus,
  MockAuthenticatedUser,
} from '../types/auth'

export const useAuthStore = defineStore('auth', () => {
  const status = ref<AuthStatus>('guest')
  const isDialogOpen = ref(false)
  const dialogScreen = ref<AuthDialogScreen>('login')
  const user = ref<MockAuthenticatedUser>({ ...mockAuthenticatedUser })
  // 로그인(Auth) 연동 전까지는 항상 null이라 대기방·매칭은 게스트 신원으로 동작한다.
  // 실제 로그인이 붙으면 여기에 JWT를 채워 회원 신원 경로가 켜진다. [[resolveIdentity]]
  const accessToken = ref<string | null>(null)

  const isAuthenticated = computed(() => status.value === 'authenticated')
  const isGuest = computed(() => status.value === 'guest')
  const displayName = computed(() =>
    isAuthenticated.value
      ? user.value.nickname
      : '\uAC8C\uC2A4\uD2B8 \uD50C\uB808\uC774\uC5B4',
  )

  function openLogin() {
    dialogScreen.value = 'login'
    isDialogOpen.value = true
  }

  function openSignup() {
    dialogScreen.value = 'signup'
    isDialogOpen.value = true
  }

  function closeDialog() {
    isDialogOpen.value = false
  }

  function signInWithMockKakao() {
    status.value = 'authenticated'
    closeDialog()
  }

  function registerMockUser() {
    const nickname = generateMockNickname()
    user.value = { ...mockAuthenticatedUser, nickname }
    status.value = 'authenticated'
    closeDialog()
    return nickname
  }

  function signOut() {
    status.value = 'guest'
    closeDialog()
  }

  return {
    status,
    isDialogOpen,
    dialogScreen,
    isAuthenticated,
    isGuest,
    displayName,
    user,
    accessToken,
    openLogin,
    openSignup,
    closeDialog,
    signInWithMockKakao,
    registerMockUser,
    signOut,
  }
})
