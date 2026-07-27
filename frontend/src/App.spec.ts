import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import App from './App.vue'
import HomePage from './pages/HomePage.vue'
import PendingPage from './pages/PendingPage.vue'

describe('App', () => {
  it('renders the home page', async () => {
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
        plugins: [router],
      },
    })

    expect(wrapper.text()).toContain('눈으로 놀고')
    expect(wrapper.text()).toContain('이번 주 랭킹 TOP 3')
    expect(wrapper.text()).toContain('© 2026 eye dont care.')

    await wrapper.get('[aria-label="프로필 메뉴"]').trigger('click')

    expect(wrapper.text()).toContain('마이페이지')

    await wrapper.get('[data-testid="quick-action-discord"]').trigger('click')

    expect(wrapper.text()).toContain(
      '디스코드 커뮤니티 연결은 다음 단계에서 준비할 예정이에요.',
    )

    await wrapper.get('[data-testid="start-games"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/games')
  })
})
