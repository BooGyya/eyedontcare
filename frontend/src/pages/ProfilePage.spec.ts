import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import ProfileMenu from '../components/layout/ProfileMenu.vue'
import { profileData } from '../mocks/profile'
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
    expect(wrapper.text()).toContain(profileData.weeklyScore)
    expect(wrapper.findAll('.profile-page__stats article')).toHaveLength(
      profileData.stats.length,
    )
    expect(wrapper.findAll('.profile-page__activities li')).toHaveLength(
      profileData.activities.length,
    )
  })

  it('updates the profile image preview when an avatar is selected', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/profile')
    await router.isReady()
    const wrapper = mount(ProfilePage, { global: { plugins: [router] } })
    const nextAvatar = profileData.avatars[1]

    await wrapper.findAll('[role="radio"]')[1].trigger('click')

    expect(
      wrapper.get('.profile-page__avatar img').attributes('src'),
    ).toContain(nextAvatar.image)
    expect(
      wrapper.findAll('[role="radio"]')[1].attributes('aria-checked'),
    ).toBe('true')
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
