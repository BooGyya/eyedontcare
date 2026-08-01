import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ProfileMenu from '../components/layout/ProfileMenu.vue'
import { profileData } from '../mocks/profile'
import { PROFILE_OPTIONS } from '../api/user'
import { useAuthStore } from '../stores/auth'
import ProfilePage from './ProfilePage.vue'
import type { AuthUser } from '../types/auth'

const routes = [
  { path: '/', name: 'home', component: { template: '<div>home</div>' } },
  { path: '/profile', name: 'profile', component: ProfilePage },
  {
    path: '/settings',
    name: 'settings',
    component: { template: '<div>settings</div>' },
  },
]

function memberUser(overrides: Partial<AuthUser> = {}): AuthUser {
  return {
    id: 5,
    nickname: profileData.nickname,
    level: 1,
    avatar: PROFILE_OPTIONS[0].image,
    profileImageCode: 'PROFILE_1',
    email: 'player@example.com',
    loginType: 'LOCAL',
    ...overrides,
  }
}

/** 엔벨로프 응답을 돌려주는 fetch 스텁. handler가 URL/옵션으로 data를 만든다. */
function stubFetch(handler: (url: string, method: string) => unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: { method?: string }) => ({
      ok: true,
      status: 200,
      json: async () => ({
        code: 'OK',
        message: '',
        data: handler(url, init?.method ?? 'GET'),
      }),
    })),
  )
}

function setupAuthenticatedPage(user: AuthUser = memberUser()) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setAuthenticatedUser(user)
  return pinia
}

describe('ProfilePage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    // Teleport로 body에 남은 다이얼로그가 있으면 다음 테스트에 새지 않도록 정리한다.
    globalThis.document.body.querySelectorAll('.profile-dialog').forEach((el) => {
      el.closest('[class*="dialog"]')?.remove()
    })
  })

  it('renders the profile data from a direct profile route', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })

    expect(wrapper.text()).toContain(profileData.nickname)
    expect(wrapper.text()).toContain('최근 경기 기록')
    expect(wrapper.findAll('.profile-page__records li')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('이번 주 점수')
  })

  it('saves the selected profile image through the API', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    const nextOption = PROFILE_OPTIONS[1]
    stubFetch(() => ({
      id: 5,
      email: 'player@example.com',
      nickname: profileData.nickname,
      profileImageCode: nextOption.code,
      loginType: 'LOCAL',
      createdAt: '2026-08-01T00:00:00Z',
    }))
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.get('.profile-page__edit-button').trigger('click')
    await wrapper.findAll('[role="radio"]')[1].trigger('click')
    await wrapper.get('.profile-page__save-button').trigger('click')
    await flushPromises()

    expect(
      wrapper.get('.profile-page__avatar img').attributes('src'),
    ).toContain(nextOption.image)
    expect(wrapper.findAll('[role="radio"]')).toHaveLength(0)
  })

  it('no longer shows a password confirmation field in the profile editor', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.get('.profile-page__edit-button').trigger('click')

    const editor = wrapper.find('.profile-page__editor')
    expect(editor.findAll('input[type="password"]')).toHaveLength(0)
    expect(editor.text()).not.toContain('새 비밀번호')
  })

  it('updates the navigation nickname after the profile nickname is saved', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    stubFetch((url) => {
      if (url.includes('/nickname/check')) {
        return { nickname: '새로운눈', available: true }
      }
      return {
        id: 5,
        email: 'player@example.com',
        nickname: '새로운눈',
        profileImageCode: 'PROFILE_1',
        loginType: 'LOCAL',
        createdAt: '2026-08-01T00:00:00Z',
      }
    })
    const pageWrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    const menuWrapper = mount(ProfileMenu, {
      global: { plugins: [pinia, router] },
    })

    await pageWrapper.get('.profile-page__edit-button').trigger('click')
    await pageWrapper.get('input[type="text"]').setValue('새로운눈')
    await pageWrapper.get('.profile-page__field button').trigger('click')
    await flushPromises()
    await pageWrapper.get('.profile-page__save-button').trigger('click')
    await flushPromises()

    expect(menuWrapper.text()).toContain('새로운눈')
  })

  it('opens and closes the selected game record detail modal', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.find('.profile-page__records li').trigger('click')
    expect(document.body.textContent).toContain('경기 결과')

    const confirmButton = document.body.querySelector<HTMLButtonElement>(
      '.game-result-modal__confirm',
    )
    confirmButton?.click()
    await flushPromises()
    expect(document.body.querySelector('.game-result-modal')).toBeNull()
  })

  it('changes password through the account dialog', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    stubFetch(() => null)
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.get('.profile-page__account-actions button').trigger('click')

    const dialog = document.body.querySelector('.profile-dialog')
    expect(dialog?.textContent).toContain('비밀번호 변경')
    const passwordInputs = document.body.querySelectorAll<HTMLInputElement>(
      '.profile-dialog input[type="password"]',
    )
    expect(passwordInputs).toHaveLength(3)

    passwordInputs[0].value = 'current-pw1'
    passwordInputs[0].dispatchEvent(new Event('input'))
    passwordInputs[1].value = 'newpass123'
    passwordInputs[1].dispatchEvent(new Event('input'))
    passwordInputs[2].value = 'newpass123'
    passwordInputs[2].dispatchEvent(new Event('input'))
    await flushPromises()

    const confirmButton = Array.from(
      document.body.querySelectorAll<HTMLButtonElement>(
        '.profile-dialog__actions button',
      ),
    ).find((button) => button.textContent?.includes('변경하기'))
    confirmButton?.click()
    await flushPromises()
    expect(document.body.querySelector('.profile-dialog')).toBeNull()
  })

  it('confirms before withdrawing', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    stubFetch(() => null)
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.get('.profile-page__withdraw-button').trigger('click')

    const dialog = document.body.querySelector('.profile-dialog')
    expect(dialog?.textContent).toContain('정말 탈퇴하시겠어요?')

    const cancelButton = Array.from(
      document.body.querySelectorAll<HTMLButtonElement>(
        '.profile-dialog__actions button',
      ),
    ).find((button) => button.textContent?.includes('취소'))
    cancelButton?.click()
    await flushPromises()
    expect(document.body.querySelector('.profile-dialog')).toBeNull()

    await wrapper.get('.profile-page__withdraw-button').trigger('click')
    const dangerButton = document.body.querySelector<HTMLButtonElement>(
      '.profile-dialog__danger-button',
    )
    dangerButton?.click()
    await flushPromises()
    expect(document.body.querySelector('.profile-dialog')).toBeNull()
  })

  it('keeps the header profile menu route to the profile page', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/settings')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    const wrapper = mount(ProfileMenu, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.get('[aria-label="프로필 메뉴"]').trigger('click')
    await wrapper.get('a[href="/profile"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/profile')
  })

  it('shows only profile and logout actions in the profile menu', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    const wrapper = mount(ProfileMenu, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.get('[aria-label="프로필 메뉴"]').trigger('click')

    expect(wrapper.find('.profile-menu__actions').text()).toContain(
      '마이페이지',
    )
    expect(wrapper.find('.profile-menu__actions').text()).toContain('로그아웃')
    expect(wrapper.find('a[href="/settings"]').exists()).toBe(false)
  })
})
