import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import ProfileMenu from '../components/layout/ProfileMenu.vue'
import { profileData } from '../mocks/profile'
import { useAuthStore } from '../stores/auth'
import ProfilePage from './ProfilePage.vue'

const routes = [
  { path: '/profile', name: 'profile', component: ProfilePage },
  {
    path: '/settings',
    name: 'settings',
    component: { template: '<div>settings</div>' },
  },
]

describe('ProfilePage', () => {
  it('renders the profile data from a direct profile route', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setAuthenticatedUser({
      id: 1,
      nickname: profileData.nickname,
      level: 1,
      avatar: 'avatar.png',
      email: null,
      loginType: 'LOCAL',
    })
    const wrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })

    expect(wrapper.text()).toContain(profileData.nickname)
    expect(wrapper.text()).toContain('최근 경기 기록')
    expect(wrapper.findAll('.profile-page__records li')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('이번 주 점수')
  })

  it('updates the profile image preview when an avatar is saved', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const wrapper = mount(ProfilePage, {
      global: { plugins: [createPinia(), router] },
    })
    const nextAvatar = profileData.avatars[1]

    await wrapper.get('.profile-page__edit-button').trigger('click')
    await wrapper.findAll('[role="radio"]')[1].trigger('click')
    await wrapper.get('.profile-page__save-button').trigger('click')

    expect(
      wrapper.get('.profile-page__avatar img').attributes('src'),
    ).toContain(nextAvatar.image)
    expect(wrapper.findAll('[role="radio"]')).toHaveLength(0)
  })

  it('no longer shows a password confirmation field in the profile editor', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const wrapper = mount(ProfilePage, {
      global: { plugins: [createPinia(), router] },
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
    const pinia = createPinia()
    const pageWrapper = mount(ProfilePage, {
      global: { plugins: [pinia, router] },
    })
    const menuWrapper = mount(ProfileMenu, {
      global: { plugins: [pinia, router] },
    })

    await pageWrapper.get('.profile-page__edit-button').trigger('click')
    await pageWrapper.get('input[type="text"]').setValue('새로운눈')
    await pageWrapper.get('.profile-page__field button').trigger('click')
    await pageWrapper.get('.profile-page__save-button').trigger('click')

    expect(menuWrapper.text()).toContain('새로운눈')
    useAuthStore(pinia).user.nickname = profileData.nickname
  })

  it('opens and closes the selected game record detail modal', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const wrapper = mount(ProfilePage, {
      global: { plugins: [createPinia(), router] },
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
    const wrapper = mount(ProfilePage, { global: { plugins: [router] } })

    await wrapper.get('.profile-page__account-actions button').trigger('click')

    const dialog = document.body.querySelector('.profile-dialog')
    expect(dialog?.textContent).toContain('비밀번호 변경')
    const passwordInputs = document.body.querySelectorAll<HTMLInputElement>(
      '.profile-dialog input[type="password"]',
    )
    expect(passwordInputs).toHaveLength(3)

    const confirmButton = Array.from(
      document.body.querySelectorAll<HTMLButtonElement>(
        '.profile-dialog__actions button',
      ),
    ).find((button) => button.textContent?.includes('변경하기'))

    confirmButton?.click()
    await flushPromises()
    expect(document.body.querySelector('.profile-dialog')).not.toBeNull()

    passwordInputs[0].value = 'current-pw'
    passwordInputs[0].dispatchEvent(new Event('input'))
    passwordInputs[1].value = 'new-pw'
    passwordInputs[1].dispatchEvent(new Event('input'))
    passwordInputs[2].value = 'new-pw'
    passwordInputs[2].dispatchEvent(new Event('input'))
    await flushPromises()

    confirmButton?.click()
    await flushPromises()
    expect(document.body.querySelector('.profile-dialog')).toBeNull()
  })

  it('confirms before withdrawing', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const wrapper = mount(ProfilePage, { global: { plugins: [router] } })

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
    const wrapper = mount(ProfileMenu, {
      global: { plugins: [createPinia(), router] },
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
    const wrapper = mount(ProfileMenu, {
      global: { plugins: [createPinia(), router] },
    })

    await wrapper.get('[aria-label="프로필 메뉴"]').trigger('click')

    expect(wrapper.find('.profile-menu__actions').text()).toContain(
      '마이페이지',
    )
    expect(wrapper.find('.profile-menu__actions').text()).toContain('로그아웃')
    expect(wrapper.find('a[href="/settings"]').exists()).toBe(false)
  })
})
