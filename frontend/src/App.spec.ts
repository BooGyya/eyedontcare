import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import App from './App.vue'
import HomePage from './pages/HomePage.vue'
import PendingPage from './pages/PendingPage.vue'

describe('App', () => {
  it('renders the home page and opens authentication from the signed-out header', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: HomePage },
        {
          path: '/games',
          component: PendingPage,
          props: { title: '게임 목록' },
        },
        { path: '/ranking', component: PendingPage, props: { title: '랭킹' } },
        {
          path: '/community',
          component: PendingPage,
          props: { title: '소모임' },
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

    await wrapper.get('.app-header__auth-button').trigger('click')
    expect(wrapper.find('[aria-label="카카오로 시작하기"]').exists()).toBe(true)

    await wrapper.get('[data-testid="start-games"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/games')
  })
})
