import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { mockAuthenticatedUser } from '../mocks/auth'
import type { AuthDialogScreen, AuthStatus } from '../types/auth'

export const useAuthStore = defineStore('auth', () => {
  const status = ref<AuthStatus>('signed-out')
  const isDialogOpen = ref(false)
  const dialogScreen = ref<AuthDialogScreen>('login')

  const isAuthenticated = computed(() => status.value === 'authenticated')
  const isGuest = computed(() => status.value === 'guest')
  const displayName = computed(() => {
    if (isAuthenticated.value) return mockAuthenticatedUser.nickname
    if (isGuest.value) return '게스트 플레이어'
    return '로그인 필요'
  })

  function openLogin() {
    dialogScreen.value = 'login'
    isDialogOpen.value = true
  }

  function openSignup() {
    dialogScreen.value = 'signup'
    isDialogOpen.value = true
  }

  function openGuestGuide() {
    dialogScreen.value = 'guest'
    isDialogOpen.value = true
  }

  function closeDialog() {
    isDialogOpen.value = false
  }

  function signInWithMockKakao() {
    status.value = 'authenticated'
    closeDialog()
  }

  function continueAsGuest() {
    status.value = 'guest'
    closeDialog()
  }

  function signOut() {
    status.value = 'signed-out'
    closeDialog()
  }

  return {
    status,
    isDialogOpen,
    dialogScreen,
    isAuthenticated,
    isGuest,
    displayName,
    user: mockAuthenticatedUser,
    openLogin,
    openSignup,
    openGuestGuide,
    closeDialog,
    signInWithMockKakao,
    continueAsGuest,
    signOut,
  }
})
