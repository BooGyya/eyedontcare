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

    const signedOutActions = wrapper.findAll('.app-header__auth-button')
    expect(signedOutActions.map((button) => button.text())).toEqual([
      '회원가입',
      '로그인',
    ])
    expect(wrapper.find('.app-header__coin').exists()).toBe(false)
    expect(wrapper.find('.app-header__icon-button').exists()).toBe(false)

    await signedOutActions[0]!.trigger('click')
    expect(wrapper.text()).toContain('눈으로 즐기는 휴식')
    expect(wrapper.text()).toContain('이미 계정이 있나요?')

    await wrapper.get('.auth-dialog__switch button').trigger('click')
    expect(wrapper.text()).toContain('로그인하고 눈으로 즐기는 게임')

    await signedOutActions[1]!.trigger('click')
    expect(wrapper.text()).toContain('카카오로 시작하기')

    await wrapper.get('[data-testid="start-games"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/games')
  })
})
