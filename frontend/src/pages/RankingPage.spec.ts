import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import { gameRankings } from '../mocks/pages'
import RankingPage from './RankingPage.vue'

async function mountRankingPage(path = '/ranking') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/ranking', component: RankingPage }],
  })

  await router.push(path)
  await router.isReady()

  return {
    router,
    wrapper: mount(RankingPage, {
      global: { plugins: [createPinia(), router] },
    }),
  }
}

describe('RankingPage', () => {
  it('renders the first game ranking by default', async () => {
    const { wrapper } = await mountRankingPage()

    expect(wrapper.text()).toContain('Eye Show Speed (눈 깜빡이기)')
    expect(wrapper.find('[data-testid="ranking-mode-tabs"]').exists()).toBe(
      false,
    )
    expect(wrapper.get('[data-testid="podium-rank-1"]').text()).toContain('1위')
    expect(wrapper.get('[data-testid="ranking-row-4"]').text()).toContain(
      '눈사람',
    )
    expect(wrapper.find('[data-testid="ranking-current-user"]').exists()).toBe(
      false,
    )
  })

  it('switches game rankings and preserves the selected game in the query', async () => {
    const { router, wrapper } = await mountRankingPage()

    const gameButtons = wrapper
      .get('[data-testid="ranking-game-tabs"]')
      .findAll('button')
    await gameButtons[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Eye Draw (눈으로 그리기)')
    expect(router.currentRoute.value.query.game).toBe('draw')
    expect(router.currentRoute.value.query.tab).toBeUndefined()
  })

  it('loads a game ranking from the ranking query', async () => {
    const { wrapper } = await mountRankingPage('/ranking?game=hold')

    expect(wrapper.text()).toContain('Eye See (눈싸움)')
  })

  it('renders all catalog games and keeps each ranking data in sync with the selected tab', async () => {
    const { router, wrapper } = await mountRankingPage()

    const gameButtons = wrapper
      .get('[data-testid="ranking-game-tabs"]')
      .findAll('button')
    expect(gameButtons).toHaveLength(5)
    expect(gameButtons.map((button) => button.text())).toEqual([
      'Eye Show Speed',
      'Eye Draw',
      'Eye See',
      'Blink the Beat',
      'Eye Hockey',
    ])

    for (const [index, ranking] of gameRankings.entries()) {
      await gameButtons[index].trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain(ranking.gameName)
      expect(wrapper.text()).not.toContain('참여자')
      expect(wrapper.text()).not.toContain('기록 단위')
      if (index > 0) {
        expect(router.currentRoute.value.query.game).toBe(ranking.gameId)
      }
    }
  })
})
