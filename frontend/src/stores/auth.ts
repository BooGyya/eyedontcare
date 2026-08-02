import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  login as apiLogin,
  loginWithKakao as apiLoginWithKakao,
  logout as apiLogout,
  signup as apiSignup,
  withdraw as apiWithdraw,
} from '../api/auth'
import {
  avatarForProfileCode,
  getUser,
  updatePassword as apiUpdatePassword,
  updateProfile as apiUpdateProfile,
} from '../api/user'
import { decodeUserId } from '../api/jwt'
import {
  clearTokens,
  getAccessToken,
  setSessionExpiredHandler,
  setTokens,
} from '../api/authTokens'
import type {
  AuthDialogScreen,
  AuthStatus,
  AuthUser,
  ProfileImageCode,
  TokenResponse,
  UserResponse,
} from '../types/auth'

/** 백엔드에 레벨 개념이 없어 임시로 쓰는 placeholder. Phase 4에서 실제 통계로 대체. */
const PLACEHOLDER_LEVEL = 1

function guestUser(): AuthUser {
  return {
    id: null,
    nickname: '게스트 플레이어',
    level: 0,
    avatar: avatarForProfileCode(null),
    profileImageCode: null,
    email: null,
    loginType: null,
    createdAt: null,
  }
}

function toAuthUser(profile: UserResponse): AuthUser {
  return {
    id: profile.id,
    nickname: profile.nickname,
    level: PLACEHOLDER_LEVEL,
    avatar: avatarForProfileCode(profile.profileImageCode),
    profileImageCode: profile.profileImageCode,
    email: profile.email,
    loginType: profile.loginType,
    createdAt: profile.createdAt,
  }
}

export const useAuthStore = defineStore('auth', () => {
  const status = ref<AuthStatus>('guest')
  const isDialogOpen = ref(false)
  const dialogScreen = ref<AuthDialogScreen>('login')
  const user = ref<AuthUser>(guestUser())

  const isAuthenticated = computed(() => status.value === 'authenticated')
  const isGuest = computed(() => status.value === 'guest')
  const displayName = computed(() =>
    isAuthenticated.value ? user.value.nickname : '게스트 플레이어',
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

  /** 인증된 사용자 상태로 전환한다. 로그인/가입/카카오/복원이 공통으로 쓴다. */
  function setAuthenticatedUser(nextUser: AuthUser) {
    user.value = nextUser
    status.value = 'authenticated'
  }

  /** 게스트 상태로 되돌린다. 로그아웃과 세션 만료가 공통으로 쓴다. */
  function resetToGuest() {
    clearTokens()
    user.value = guestUser()
    status.value = 'guest'
  }

  /** 토큰을 저장하고, JWT의 userId로 현재 사용자를 불러와 인증 상태로 전환한다. */
  async function applyTokensAndLoadUser(tokens: TokenResponse) {
    setTokens(tokens)
    const userId = decodeUserId(tokens.accessToken)
    if (userId === null) {
      clearTokens()
      throw new Error('로그인 토큰을 해석하지 못했어요.')
    }
    const profile = await getUser(userId)
    setAuthenticatedUser(toAuthUser(profile))
  }

  async function login(email: string, password: string) {
    await applyTokensAndLoadUser(await apiLogin(email, password))
    closeDialog()
  }

  async function signup(email: string, password: string) {
    // 가입 응답이 곧바로 토큰을 주므로 별도 로그인 없이 인증 상태가 된다.
    await applyTokensAndLoadUser(await apiSignup(email, password))
    closeDialog()
  }

  async function loginWithKakao(authorizationCode: string) {
    await applyTokensAndLoadUser(await apiLoginWithKakao(authorizationCode))
    closeDialog()
  }

  /** 카카오 인가 페이지로 이동한다. 콜백(`/auth/kakao/callback`)에서 code를 받아 로그인한다. */
  function startKakaoLogin() {
    const clientId = import.meta.env.VITE_KAKAO_CLIENT_ID
    const redirectUri =
      import.meta.env.VITE_KAKAO_REDIRECT_URI ??
      `${globalThis.location.origin}/auth/kakao/callback`
    if (!clientId) {
      throw new Error('카카오 로그인이 설정되지 않았어요.')
    }
    const query = new globalThis.URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      redirect_uri: redirectUri,
    })
    globalThis.location.href = `https://kauth.kakao.com/oauth/authorize?${query.toString()}`
  }

  async function signOut() {
    // 서버의 refresh 토큰 폐기는 best-effort. 실패해도 클라 토큰은 반드시 비운다.
    try {
      await apiLogout()
    } catch {
      /* 이미 만료됐거나 네트워크 오류 — 무시하고 로컬 정리 */
    }
    resetToGuest()
    closeDialog()
  }

  /** 부팅 시 저장된 토큰으로 세션을 복원한다. 실패하면 조용히 게스트로 남는다. */
  async function restoreSession() {
    const accessToken = getAccessToken()
    if (!accessToken) return
    const userId = decodeUserId(accessToken)
    if (userId === null) {
      clearTokens()
      return
    }
    try {
      const profile = await getUser(userId)
      setAuthenticatedUser(toAuthUser(profile))
    } catch {
      // 토큰이 만료됐고 재발급도 실패한 경우 등 — 게스트로 유지.
      resetToGuest()
    }
  }

  /** 닉네임/프로필 이미지 수정. 응답(UserResponse)으로 user를 갱신해 헤더/메뉴와 즉시 일관. */
  async function updateProfile(patch: {
    nickname?: string
    profileImageCode?: ProfileImageCode
  }) {
    if (user.value.id === null) {
      throw new Error('로그인이 필요해요.')
    }
    const profile = await apiUpdateProfile(user.value.id, patch)
    setAuthenticatedUser(toAuthUser(profile))
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    if (user.value.id === null) {
      throw new Error('로그인이 필요해요.')
    }
    await apiUpdatePassword(user.value.id, { currentPassword, newPassword })
  }

  /** 회원 탈퇴 후 게스트로 되돌린다. */
  async function withdraw() {
    await apiWithdraw()
    resetToGuest()
  }

  // 자동 재발급까지 실패하면(세션 만료) 게스트로 전환한다.
  setSessionExpiredHandler(resetToGuest)

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
    setAuthenticatedUser,
    login,
    signup,
    loginWithKakao,
    startKakaoLogin,
    signOut,
    restoreSession,
    updateProfile,
    changePassword,
    withdraw,
  }
})
