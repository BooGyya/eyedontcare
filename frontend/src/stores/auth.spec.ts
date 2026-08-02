import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AuthDialog from '../components/auth/AuthDialog.vue'
import AppHeader from '../components/layout/AppHeader.vue'
import ProfileMenu from '../components/layout/ProfileMenu.vue'
import { useAuthStore } from './auth'
import type { AuthUser } from '../types/auth'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/profile', component: { template: '<div>profile</div>' } },
    ],
  })
}

function base64url(value: unknown): string {
  return globalThis
    .btoa(JSON.stringify(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

/** `sub` 클레임만 있으면 되는 가짜 access 토큰(서명은 검증하지 않음). */
function fakeAccessToken(userId: number): string {
  return `header.${base64url({ sub: userId })}.signature`
}

function envelope(data: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: async () => ({ code: 'OK', message: '', data }),
  }
}

const memberUser: AuthUser = {
  id: 7,
  nickname: '테스트눈',
  level: 1,
  avatar: 'avatar.png',
  profileImageCode: 'PROFILE_1',
  email: 'player@example.com',
  loginType: 'LOCAL',
  createdAt: '2026-08-01T00:00:00Z',
}

describe('auth store and UI', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    globalThis.localStorage?.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts as a guest and returns to guest after logout', async () => {
    const auth = useAuthStore()

    expect(auth.status).toBe('guest')
    auth.setAuthenticatedUser(memberUser)
    expect(auth.status).toBe('authenticated')
    expect(auth.displayName).toBe('테스트눈')

    await auth.signOut()
    expect(auth.status).toBe('guest')
    expect(auth.displayName).toBe('게스트 플레이어')
  })

  it('logs in through the API and loads the member profile', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/login')) {
        return envelope({ accessToken: fakeAccessToken(7), refreshToken: 'r' })
      }
      if (url.includes('/users/7')) {
        return envelope({
          id: 7,
          email: 'player@example.com',
          nickname: '테스트눈',
          profileImageCode: 'PROFILE_1',
          loginType: 'LOCAL',
          createdAt: '2026-08-01T00:00:00Z',
        })
      }
      throw new Error(`unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    const auth = useAuthStore()
    await auth.login('player@example.com', 'password1')

    expect(auth.status).toBe('authenticated')
    expect(auth.user.id).toBe(7)
    expect(auth.user.nickname).toBe('테스트눈')
    expect(globalThis.localStorage.getItem('eye-dont-care.accessToken')).toBe(
      fakeAccessToken(7),
    )
  })

  it('switches between signup and login screens and closes the auth dialog', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    const wrapper = mount(AuthDialog, {
      global: { plugins: [pinia], stubs: { Teleport: true } },
    })

    auth.openSignup()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[aria-label="카카오로 시작하기"]').exists()).toBe(true)
    expect(wrapper.find('.auth-dialog__signup-panel').exists()).toBe(true)

    await wrapper.get('.auth-dialog__switch button').trigger('click')
    expect(wrapper.text()).toContain('처음이신가요?')

    await wrapper.get('[aria-label="인증 창 닫기"]').trigger('click')
    expect(auth.isDialogOpen).toBe(false)
  })

  it('validates signup fields before calling the API', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    const wrapper = mount(AuthDialog, {
      global: { plugins: [pinia], stubs: { Teleport: true } },
    })

    auth.openSignup()
    await wrapper.vm.$nextTick()
    await wrapper.get('.auth-dialog__form').trigger('submit')

    expect(wrapper.text()).toContain('이메일을 입력해주세요.')
    expect(wrapper.text()).toContain('비밀번호를 입력해주세요.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('renders guest controls, and a profile menu once authenticated', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const auth = useAuthStore()
    const wrapper = mount(AppHeader, { global: { plugins: [pinia, router] } })

    expect(wrapper.text()).toContain('회원가입')
    expect(wrapper.text()).toContain('로그인')
    expect(wrapper.find('[aria-label="프로필 메뉴"]').exists()).toBe(false)

    auth.setAuthenticatedUser(memberUser)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[aria-label="프로필 메뉴"]').exists()).toBe(true)
  })

  it('shows the member nickname in the profile menu', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const auth = useAuthStore()
    auth.setAuthenticatedUser(memberUser)
    const wrapper = mount(ProfileMenu, {
      global: { plugins: [pinia, router] },
    })

    expect(wrapper.text()).toContain('테스트눈')
  })
})
