import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
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
      const wrapper = mount(GamePlayPage, {
        global: { plugins: [router, createPinia()] },
      })
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
      global: { plugins: [router, createPinia()] },
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

  it('shows a leave confirmation dialog before emitting leave', async () => {
    const router = createGameRouter()
    await router.push('/games/air/play?mode=solo')
    await router.isReady()
    const wrapper = mount(GamePlayPage, {
      global: { plugins: [router, createPinia()] },
    })

    await wrapper.get('.play-shell__leave').trigger('click')
    expect(wrapper.find('.play-shell__confirm').exists()).toBe(true)
    expect(wrapper.text()).toContain('게임에서 나가시겠어요?')
    expect(wrapper.text()).toContain('몰수패')

    const continueButton = wrapper
      .findAll('.play-shell__confirm-actions button')
      .find((button) => button.text() === '계속하기')
    await continueButton?.trigger('click')

    expect(wrapper.find('.play-shell__confirm').exists()).toBe(false)
    expect(wrapper.find('.play-shell').exists()).toBe(true)
    expect(router.currentRoute.value.name).toBe('game-play')
    wrapper.unmount()
  })

  it.each(['friends', 'random'])(
    'renders separate rhythm game status panels in %s mode',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/rhythm/play?mode=${mode}`)
      await router.isReady()
      const wrapper = mount(GamePlayPage, {
        global: { plugins: [router, createPinia()] },
      })

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

  it('shows the redesigned draw result summary with round score tooltips', async () => {
    const router = createGameRouter()
    await router.push('/games/draw/result?mode=solo')
    await router.isReady()
    const wrapper = mount(GameResultPage, { global: { plugins: [router] } })

    expect(wrapper.text()).toContain('게임이 종료되었습니다!')
    expect(wrapper.text()).toContain(
      '3개 라운드의 그림 인식 결과를 확인해보세요.',
    )
    expect(wrapper.text()).toContain('최종 총점')
    expect(wrapper.text()).toContain('680점')
    expect(wrapper.text()).toContain('NEW RECORD')
    expect(wrapper.text()).not.toContain('랭킹 반영 예정')

    const roundCards = wrapper.findAll('.draw-round-card')
    expect(roundCards).toHaveLength(3)
    expect(wrapper.text()).toContain('정답')
    expect(wrapper.text()).toContain('쉬움')

    const scoreButton = wrapper.get('.draw-round-card__score')
    const describedBy = scoreButton.attributes('aria-describedby')
    expect(describedBy).toBeTruthy()
    const tooltip = wrapper.find(`#${describedBy}`)
    expect(tooltip.exists()).toBe(true)
    expect(tooltip.text()).toContain('상세 점수')
    expect(tooltip.text()).toContain('시간 보너스')

    expect(wrapper.text()).toContain('랭킹 결과')
    expect(wrapper.text()).toContain('전체 랭킹 보기')
    expect(wrapper.text()).toContain('다시 플레이')

    const gamesButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '게임 목록')
    expect(gamesButton?.exists()).toBe(true)
    wrapper.unmount()
  })
})
