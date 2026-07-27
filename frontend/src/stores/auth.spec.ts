import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'
import AuthDialog from '../components/auth/AuthDialog.vue'
import AppHeader from '../components/layout/AppHeader.vue'
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

  it('starts signed out and switches through mock authentication states', () => {
    const auth = useAuthStore()

    expect(auth.status).toBe('signed-out')
    auth.signInWithMockKakao()
    expect(auth.status).toBe('authenticated')
    expect(auth.displayName).toBe(auth.user.nickname)
    auth.signOut()
    expect(auth.status).toBe('signed-out')
    auth.continueAsGuest()
    expect(auth.status).toBe('guest')
    expect(auth.displayName).toBe('게스트 플레이어')
  })

  it('opens login and guest screens and closes the auth dialog', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    const wrapper = mount(AuthDialog, {
      global: { plugins: [pinia], stubs: { Teleport: true } },
    })

    auth.openLogin()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('카카오로 시작하기')

    await wrapper.get('.auth-dialog__provider--guest').trigger('click')
    expect(wrapper.text()).toContain('게스트로 계속하기')

    await wrapper.get('[aria-label="로그인 창 닫기"]').trigger('click')
    expect(auth.isDialogOpen).toBe(false)
  })

  it('renders header controls for signed-out, guest, and authenticated states', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const auth = useAuthStore()
    const wrapper = mount(AppHeader, { global: { plugins: [pinia, router] } })

    expect(wrapper.text()).toContain('로그인')
    expect(wrapper.find('[aria-label="프로필 메뉴"]').exists()).toBe(false)

    auth.continueAsGuest()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('게스트')

    auth.signInWithMockKakao()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[aria-label="프로필 메뉴"]').exists()).toBe(true)
  })
})
