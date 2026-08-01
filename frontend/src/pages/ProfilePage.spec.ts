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

const EMPTY_RESULTS_PAGE = { content: [], page: 1, size: 5, totalElements: 0 }

const RESULTS_PAGE = {
  content: [
    {
      resultId: 9001,
      gameName: 'EYEFIGHT',
      playMode: 'RANDOM',
      difficulty: null,
      myOutcome: 'WIN',
      myRank: 1,
      playedAt: '2026-07-24T09:00:00Z',
    },
    {
      resultId: 9002,
      gameName: 'DRAWING',
      playMode: 'SOLO',
      difficulty: 2,
      myOutcome: 'COMPLETED',
      myRank: 1,
      playedAt: '2026-07-23T20:40:00Z',
    },
    {
      resultId: 9003,
      gameName: 'BLINK',
      playMode: 'SOLO',
      difficulty: null,
      myOutcome: 'LOSE',
      myRank: 4,
      playedAt: '2026-07-22T10:12:00Z',
    },
  ],
  page: 1,
  size: 5,
  totalElements: 3,
}

const RESULT_DETAIL = {
  resultId: 9001,
  gameName: 'EYEFIGHT',
  playMode: 'RANDOM',
  difficulty: null,
  startedAt: '2026-07-24T09:00:00Z',
  endedAt: '2026-07-24T09:03:00Z',
  participants: [
    { slotNo: 1, participantType: 'USER', displayName: '나', outcome: 'WIN', rank: 1 },
    { slotNo: 2, participantType: 'USER', displayName: '상대', outcome: 'LOSE', rank: 2 },
  ],
  gameResult: { '1': { survivalTimeMs: 180000 } },
}

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

/**
 * 엔벨로프 응답 fetch 스텁. handler가 undefined를 반환하면 기본값으로 대체한다
 * (경기 기록 목록은 빈 페이지, 그 외는 null) — 각 테스트가 자기 URL만 신경 쓰면 된다.
 */
function stubFetch(handler: (url: string, method: string) => unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: { method?: string }) => {
      let data = handler(url, init?.method ?? 'GET')
      if (data === undefined) {
        data = url.includes('/game-results/me') ? EMPTY_RESULTS_PAGE : null
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({ code: 'OK', message: '', data }),
      }
    }),
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
    globalThis.document.body
      .querySelectorAll('.profile-dialog, .game-result-modal')
      .forEach((el) => el.remove())
  })

  it('renders profile data and the fetched game records', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    stubFetch((url) =>
      url.includes('/game-results/me') ? RESULTS_PAGE : undefined,
    )
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain(profileData.nickname)
    expect(wrapper.text()).toContain('최근 경기 기록')
    expect(wrapper.findAll('.profile-page__records li')).toHaveLength(3)
    expect(wrapper.text()).toContain('눈싸움에서 1위로 승리했어요.')
  })

  it('prompts guests to log in for game records', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = createPinia()
    setActivePinia(pinia)
    stubFetch(() => undefined)
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('로그인하면 내 경기 기록을 볼 수 있어요')
    expect(wrapper.findAll('.profile-page__records li')).toHaveLength(0)
  })

  it('saves the selected profile image through the API', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    const nextOption = PROFILE_OPTIONS[1]
    stubFetch((_url, method) =>
      method === 'PATCH'
        ? {
            id: 5,
            email: 'player@example.com',
            nickname: profileData.nickname,
            profileImageCode: nextOption.code,
            loginType: 'LOCAL',
            createdAt: '2026-08-01T00:00:00Z',
          }
        : undefined,
    )
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

    await wrapper.get('.profile-page__edit-button').trigger('click')
    await wrapper.findAll('[role="radio"]')[1].trigger('click')
    await wrapper.get('.profile-page__save-button').trigger('click')
    await flushPromises()

    expect(
      wrapper.get('.profile-page__avatar img').attributes('src'),
    ).toContain(nextOption.image)
  })

  it('no longer shows a password confirmation field in the profile editor', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    stubFetch(() => undefined)
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

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
    stubFetch((url, method) => {
      if (url.includes('/nickname/check')) {
        return { nickname: '새로운눈', available: true }
      }
      if (method === 'PATCH') {
        return {
          id: 5,
          email: 'player@example.com',
          nickname: '새로운눈',
          profileImageCode: 'PROFILE_1',
          loginType: 'LOCAL',
          createdAt: '2026-08-01T00:00:00Z',
        }
      }
      return undefined
    })
    const pageWrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    const menuWrapper = mount(ProfileMenu, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

    await pageWrapper.get('.profile-page__edit-button').trigger('click')
    await pageWrapper.get('input[type="text"]').setValue('새로운눈')
    await pageWrapper.get('.profile-page__field button').trigger('click')
    await flushPromises()
    await pageWrapper.get('.profile-page__save-button').trigger('click')
    await flushPromises()

    expect(menuWrapper.text()).toContain('새로운눈')
  })

  it('opens and closes the game record detail modal', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = setupAuthenticatedPage()
    stubFetch((url) => {
      if (url.includes('/game-results/me')) return RESULTS_PAGE
      if (url.includes('/game-results/9001')) return RESULT_DETAIL
      return undefined
    })
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

    await wrapper.find('.profile-page__records li').trigger('click')
    await flushPromises()
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
    stubFetch(() => undefined)
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

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
    stubFetch(() => undefined)
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

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
