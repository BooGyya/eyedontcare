import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import ProfileMenu from '../components/layout/ProfileMenu.vue'
import { profileData, profileState } from '../mocks/profile'
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
    const wrapper = mount(ProfilePage, { global: { plugins: [router] } })

    expect(wrapper.text()).toContain(profileData.nickname)
    expect(wrapper.text()).toContain('최근 경기 기록')
    expect(wrapper.findAll('.profile-page__records li')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('이번 주 점수')
  })

  it('updates the profile image preview when an avatar is saved', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const wrapper = mount(ProfilePage, { global: { plugins: [router] } })
    const nextAvatar = profileData.avatars[1]

    await wrapper.get('.profile-page__edit-button').trigger('click')
    await wrapper.findAll('[role="radio"]')[1].trigger('click')
    await wrapper.get('.profile-page__save-button').trigger('click')

    expect(
      wrapper.get('.profile-page__avatar img').attributes('src'),
    ).toContain(nextAvatar.image)
    expect(wrapper.findAll('[role="radio"]')).toHaveLength(0)
  })

  it('shows password confirmation feedback in the profile editor', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const wrapper = mount(ProfilePage, { global: { plugins: [router] } })

    await wrapper.get('.profile-page__edit-button').trigger('click')
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('new-password')
    await passwordInputs[1].setValue('different-password')

    expect(wrapper.text()).toContain('비밀번호가 일치하지 않아요.')
  })

  it('updates the navigation nickname after the profile nickname is saved', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const pageWrapper = mount(ProfilePage, { global: { plugins: [router] } })
    const menuWrapper = mount(ProfileMenu, { global: { plugins: [router] } })

    await pageWrapper.get('.profile-page__edit-button').trigger('click')
    await pageWrapper.get('input[type="text"]').setValue('새로운눈')
    await pageWrapper.get('.profile-page__field button').trigger('click')
    await pageWrapper.get('.profile-page__save-button').trigger('click')

    expect(menuWrapper.text()).toContain('새로운눈')
    profileState.nickname = profileData.nickname
  })

  it('opens and closes the selected game record detail modal', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const wrapper = mount(ProfilePage, { global: { plugins: [router] } })

    await wrapper.find('.profile-page__records li').trigger('click')
    expect(document.body.textContent).toContain('경기 결과')

    const confirmButton = document.body.querySelector<HTMLButtonElement>(
      '.game-result-modal__confirm',
    )
    confirmButton?.click()
    await flushPromises()
    expect(document.body.querySelector('.game-result-modal')).toBeNull()
  })

  it('keeps the header profile menu route to the profile page', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/settings')
    await router.isReady()
    const wrapper = mount(ProfileMenu, { global: { plugins: [router] } })

    await wrapper.get('[aria-label="프로필 메뉴"]').trigger('click')
    await wrapper.get('a[href="/profile"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/profile')
  })
})
