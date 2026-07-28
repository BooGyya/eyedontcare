import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'
import AuthDialog from '../components/auth/AuthDialog.vue'
import AppHeader from '../components/layout/AppHeader.vue'
import ProfileMenu from '../components/layout/ProfileMenu.vue'
import AccountPage from '../pages/AccountPage.vue'
import { useAuthStore } from './auth'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/profile', component: { template: '<div>profile</div>' } },
      {
        path: '/notifications',
        component: { template: '<div>notifications</div>' },
      },
      { path: '/settings', component: { template: '<div>settings</div>' } },
    ],
  })
}

describe('auth store and UI', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts as a guest and returns to guest after mock authentication', () => {
    const auth = useAuthStore()

    expect(auth.status).toBe('guest')
    auth.signInWithMockKakao()
    expect(auth.status).toBe('authenticated')
    expect(auth.displayName).toBe(auth.user.nickname)
    auth.signOut()
    expect(auth.status).toBe('guest')
    expect(auth.displayName).toBe('게스트 플레이어')
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
    expect(wrapper.text()).not.toContain('비밀번호 확인')
    expect(wrapper.find('.auth-dialog__signup-panel').exists()).toBe(true)

    await wrapper.get('.auth-dialog__switch button').trigger('click')
    expect(wrapper.text()).toContain('처음이신가요?')
    expect(wrapper.text()).not.toContain('비밀번호 확인')

    await wrapper.get('[aria-label="인증 창 닫기"]').trigger('click')
    expect(auth.isDialogOpen).toBe(false)
  })

  it('renders signup and login controls for guests and a profile menu for members', async () => {
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

    auth.signInWithMockKakao()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[aria-label="프로필 메뉴"]').exists()).toBe(true)
  })

  it('creates a random nickname without retaining form credentials', () => {
    const auth = useAuthStore()

    const nickname = auth.registerMockUser()

    expect(auth.status).toBe('authenticated')
    expect(auth.user.nickname).toBe(nickname)
    expect(nickname).not.toBe('')
  })

  it('validates signup fields before creating a mock member', async () => {
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

    await wrapper.get('#signup-email').setValue('new-player@example.com')
    await wrapper.get('#signup-password').setValue('password1')
    await wrapper.get('.auth-dialog__form').trigger('submit')
    expect(auth.status).toBe('authenticated')
    expect(auth.user.nickname).not.toBe('')
  })

  it('shows a generated nickname in the profile menu', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const auth = useAuthStore()
    const nickname = auth.registerMockUser()
    const wrapper = mount(ProfileMenu, {
      global: { plugins: [pinia, router] },
    })

    expect(wrapper.text()).toContain(nickname)
  })

  it('shows the generated nickname in the profile page summary', () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    const nickname = auth.registerMockUser()
    const wrapper = mount(AccountPage, {
      props: {
        title: '마이페이지',
        description: '내 활동과 게임 기록을 한눈에 확인해 보세요.',
        items: ['내 프로필'],
      },
      global: { plugins: [pinia] },
    })

    expect(wrapper.get('.account-page__profile').text()).toContain(nickname)
  })
})
