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

    for (let count = 0; count < 5; count += 1) {
      await opponentMiss.trigger('click')
    }

    await nextTick()
    await new Promise((resolve) => globalThis.setTimeout(resolve, 0))
    expect(router.currentRoute.value.name).toBe('game-result')
    wrapper.unmount()
  })

  it('shows the completed result when mock game end is selected', async () => {
    const router = createGameRouter()
    await router.push('/games/rhythm/play?mode=solo&result=failed')
    await router.isReady()
    const wrapper = mount(GamePlayPage, { global: { plugins: [router] } })

    await wrapper.get('.finish').trigger('click')
    await nextTick()
    await new Promise((resolve) => globalThis.setTimeout(resolve, 0))
    expect(router.currentRoute.value.name).toBe('game-result')
    expect(router.currentRoute.value.query.result).toBeUndefined()
    wrapper.unmount()
  })

  it('shows the failed result when the solo player uses every rhythm heart', async () => {
    const router = createGameRouter()
    await router.push('/games/rhythm/play?mode=solo')
    await router.isReady()
    const wrapper = mount(GamePlayPage, { global: { plugins: [router] } })
    const mineMiss = wrapper.get('.rhythm-controls button:nth-child(3)')

    for (let count = 0; count < 5; count += 1) {
      await mineMiss.trigger('click')
    }

    await nextTick()
    await new Promise((resolve) => globalThis.setTimeout(resolve, 0))
    expect(router.currentRoute.value.name).toBe('game-result')
    expect(router.currentRoute.value.query.result).toBe('failed')
    wrapper.unmount()
  })

  it.each(['friends', 'random'])(
    'shows the rhythm duel loss result in %s mode when my hearts are depleted',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/rhythm/play?mode=${mode}`)
      await router.isReady()
      const wrapper = mount(GamePlayPage, { global: { plugins: [router] } })
      const mineMiss = wrapper.get('.rhythm-controls button:nth-child(3)')

      for (let count = 0; count < 5; count += 1) {
        await mineMiss.trigger('click')
      }

      await nextTick()
      await new Promise((resolve) => globalThis.setTimeout(resolve, 0))
      expect(router.currentRoute.value.query.result).toBe('lose')
      wrapper.unmount()
    },
  )

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

  it('renders the heart-depletion result message', async () => {
    const router = createGameRouter()
    await router.push('/games/rhythm/result?mode=solo&result=failed')
    await router.isReady()
    const wrapper = mount(GameResultPage, { global: { plugins: [router] } })

    expect(wrapper.find('.failed-result').classes()).toContain('failed-result')
    expect(wrapper.text()).toContain('게임에 실패했어요!')
    expect(wrapper.text()).toContain('하트를 모두 사용했어요!')
    wrapper.unmount()
  })

  it('renders the solo Eye See result when a record is not renewed', async () => {
    const router = createGameRouter()
    await router.push('/games/hold/result?mode=solo&result=not-new-record')
    await router.isReady()
    const wrapper = mount(GameResultPage, { global: { plugins: [router] } })

    expect(wrapper.find('.hold-record-missed').exists()).toBe(true)
    expect(wrapper.text()).toContain('신기록 갱신에 실패했어요!')
    expect(wrapper.text()).toContain('01:02.38')
    expect(wrapper.find('.result-actions').exists()).toBe(false)
    wrapper.unmount()
  })

  it.each(['friends', 'random'])(
    'renders the rhythm duel loss result in %s mode',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/rhythm/result?mode=${mode}&result=lose`)
      await router.isReady()
      const wrapper = mount(GameResultPage, { global: { plugins: [router] } })

      expect(wrapper.find('.duel-loss').exists()).toBe(true)
      expect(wrapper.text()).toContain('YOU LOSE...')
      wrapper.unmount()
    },
  )

  it.each(['ai', 'friends', 'random'])(
    'renders the air hockey loss result in %s mode',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/air/result?mode=${mode}&result=lose`)
      await router.isReady()
      const wrapper = mount(GameResultPage, { global: { plugins: [router] } })

      expect(wrapper.find('.air-result').exists()).toBe(true)
      expect(wrapper.find('.air-result .duel-loss__hero').exists()).toBe(true)
      expect(wrapper.text()).toContain('YOU LOSE...')
      expect(wrapper.text()).not.toContain('승리한 플레이어')
      expect(wrapper.text()).not.toContain('아쉬운 플레이어')
      wrapper.unmount()
    },
  )

  it.each(['ai', 'friends', 'random'])(
    'renders the air hockey victory result in %s mode with its banner and score panel',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/air/result?mode=${mode}`)
      await router.isReady()
      const wrapper = mount(GameResultPage, { global: { plugins: [router] } })

      expect(wrapper.find('.air-result').exists()).toBe(true)
      expect(wrapper.find('.air-result .duel-loss__hero').exists()).toBe(true)
      expect(wrapper.text()).toContain('YOU WIN!')
      expect(wrapper.text()).not.toContain('승리한 플레이어')
      expect(wrapper.text()).not.toContain('아쉬운 플레이어')
      if (mode !== 'ai') {
        expect(wrapper.text()).toContain('눈빛 좋은 플레이어')
        expect(wrapper.text()).toContain('신나는 플레이어')
      }
      wrapper.unmount()
    },
  )

  it.each(['friends', 'random'])(
    'renders the Eye See duel result in %s mode with the showcase result layout',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/hold/result?mode=${mode}`)
      await router.isReady()
      const wrapper = mount(GameResultPage, { global: { plugins: [router] } })

      expect(wrapper.find('.air-result').exists()).toBe(true)
      expect(wrapper.find('.air-result .duel-loss__hero').exists()).toBe(true)
      expect(wrapper.text()).toContain('YOU WIN!')
      expect(wrapper.text()).toContain('눈빛 좋은 플레이어')
      expect(wrapper.text()).toContain('신나는 플레이어')
      wrapper.unmount()
    },
  )

  it.each(['friends', 'random'])(
    'renders the rhythm duel victory result in %s mode with the shared duel layout',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/rhythm/result?mode=${mode}`)
      await router.isReady()
      const wrapper = mount(GameResultPage, { global: { plugins: [router] } })

      expect(wrapper.find('.duel-loss').exists()).toBe(true)
      expect(wrapper.text()).toContain('YOU WIN!')
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
