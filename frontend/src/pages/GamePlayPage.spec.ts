import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import GamePlayPage from './GamePlayPage.vue'
import GameResultPage from './GameResultPage.vue'

function createGameRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/games/:gameId/play',
        name: 'game-play',
        component: GamePlayPage,
      },
      {
        path: '/games/:gameId/result',
        name: 'game-result',
        component: GameResultPage,
      },
    ],
  })
}

describe('gameplay routes', () => {
  it.each(['air', 'hold', 'draw', 'rhythm', 'blink'])(
    'renders the %s play route from mock data',
    async (gameId) => {
      const router = createGameRouter()
      await router.push(`/games/${gameId}/play?mode=solo`)
      await router.isReady()
      const wrapper = mount(GamePlayPage, { global: { plugins: [router] } })
      expect(wrapper.find('.play-shell').exists()).toBe(true)
      wrapper.unmount()
    },
  )

  it('shows each draw round result and advances to the next round', async () => {
    const router = createGameRouter()
    await router.push('/games/draw/play?mode=solo')
    await router.isReady()
    const wrapper = mount(GamePlayPage, {
      attachTo: document.body,
      global: { plugins: [router] },
    })

    await wrapper.get('.draw-tools .primary').trigger('click')
    expect(document.body.textContent).toContain('ROUND 1 점수')
    expect(document.body.textContent).toContain('180점')

    const nextButton =
      document.body.querySelector<HTMLButtonElement>('.dialog-action')
    await nextButton?.click()
    await nextTick()
    expect(wrapper.text()).toContain('Round 2 / 3')
    expect(wrapper.text()).toContain('우산')
    wrapper.unmount()
  })

  it.each(['friends', 'random'])(
    'renders separate rhythm game status panels in %s mode',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/rhythm/play?mode=${mode}`)
      await router.isReady()
      const wrapper = mount(GamePlayPage, { global: { plugins: [router] } })

      expect(wrapper.findAll('.rhythm-duel-player')).toHaveLength(2)
      expect(wrapper.text()).toContain('상대 게임 화면')
      expect(wrapper.text()).toContain('체력')
      wrapper.unmount()
    },
  )

  it('moves to the result screen when a rhythm duel player loses all health', async () => {
    const router = createGameRouter()
    await router.push('/games/rhythm/play?mode=friends')
    await router.isReady()
    const wrapper = mount(GamePlayPage, { global: { plugins: [router] } })
    const opponentMiss = wrapper.get('[data-testid="opponent-rhythm-miss"]')

    for (let count = 0; count < 4; count += 1) {
      await opponentMiss.trigger('click')
    }

    await nextTick()
    await new Promise((resolve) => globalThis.setTimeout(resolve, 0))
    expect(router.currentRoute.value.name).toBe('game-result')
    wrapper.unmount()
  })

  it.each(['air', 'hold', 'draw', 'rhythm', 'blink'])(
    'renders the %s result route from mock data',
    async (gameId) => {
      const router = createGameRouter()
      await router.push(`/games/${gameId}/result?mode=solo`)
      await router.isReady()
      const wrapper = mount(GameResultPage, { global: { plugins: [router] } })
      expect(wrapper.find('.result-shell').exists()).toBe(true)
      wrapper.unmount()
    },
  )
})
