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

  const isAuthenticated = computed(() => status.value === 'authenticated')
  const isGuest = computed(() => status.value === 'guest')
  const displayName = computed(() => {
    if (isAuthenticated.value) return user.value.nickname
    return '게스트 플레이어'
  })

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
    openLogin,
    openSignup,
    closeDialog,
    signInWithMockKakao,
    registerMockUser,
    signOut,
  }
})
