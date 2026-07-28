import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import App from './App.vue'
import HomePage from './pages/HomePage.vue'
import PendingPage from './pages/PendingPage.vue'

describe('App', () => {
  it('renders the home page and supports authentication entry from the header', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: HomePage },
        { path: '/games', component: PendingPage, props: { title: 'Games' } },
        {
          path: '/ranking',
          component: PendingPage,
          props: { title: 'Ranking' },
        },
        {
          path: '/community',
          component: PendingPage,
          props: { title: 'Community' },
        },
      ],
    })

    await router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
        stubs: { Teleport: true },
      },
    })

    const authActions = wrapper.findAll('.app-header__auth-button')
    expect(authActions).toHaveLength(2)
    expect(wrapper.find('.app-header__coin').exists()).toBe(false)
    expect(wrapper.find('.app-header__icon-button').exists()).toBe(false)

    await authActions[0]!.trigger('click')
    expect(wrapper.find('#signup-email').exists()).toBe(true)

    await wrapper.get('.auth-dialog__switch button').trigger('click')
    expect(wrapper.find('#login-email').exists()).toBe(true)

    await wrapper.get('[data-testid="start-games"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/games')
  })
})
