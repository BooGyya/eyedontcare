import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import { gameCatalog, gameRankings } from '../mocks/pages'
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
    wrapper: mount(RankingPage, { global: { plugins: [router] } }),
  }
}

describe('RankingPage', () => {
  it('renders the overall ranking by default', async () => {
    const { wrapper } = await mountRankingPage()

    expect(wrapper.text()).toContain('전체 랭킹')
    expect(wrapper.get('[data-testid="podium-rank-1"]').text()).toContain('1위')
    expect(wrapper.get('[data-testid="ranking-row-4"]').text()).toContain(
      '눈쌩 최강자',
    )
    expect(wrapper.find('[data-testid="ranking-current-user"]').exists()).toBe(
      false,
    )
  })

  it('switches to game rankings and preserves the selected game in the query', async () => {
    const { router, wrapper } = await mountRankingPage()
    const modeButtons = wrapper
      .get('[data-testid="ranking-mode-tabs"]')
      .findAll('button')

    await modeButtons[1].trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="ranking-game-tabs"]').text()).toContain(
      '눈 깜빡이기',
    )
    expect(router.currentRoute.value.query.tab).toBe('games')

    const gameButtons = wrapper
      .get('[data-testid="ranking-game-tabs"]')
      .findAll('button')
    await gameButtons[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('눈으로 그리기')
    expect(router.currentRoute.value.query.game).toBe('draw')
  })

  it('loads a game ranking from the ranking query', async () => {
    const { wrapper } = await mountRankingPage('/ranking?tab=games&game=hold')

    expect(wrapper.text()).toContain('Eye-See')
  })

  it('renders all catalog games and keeps each ranking data in sync with the selected tab', async () => {
    const { router, wrapper } = await mountRankingPage()
    await wrapper
      .get('[data-testid="ranking-mode-tabs"]')
      .findAll('button')[1]
      .trigger('click')
    await flushPromises()

    const gameButtons = wrapper
      .get('[data-testid="ranking-game-tabs"]')
      .findAll('button')
    expect(gameButtons).toHaveLength(5)
    expect(gameButtons.map((button) => button.text())).toEqual(
      gameCatalog.map((game) => game.title),
    )

    for (const [index, game] of gameCatalog.entries()) {
      const ranking = gameRankings.find((item) => item.gameId === game.id)!
      await gameButtons[index].trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain(ranking.gameName)
      expect(wrapper.text()).toContain(`참여자 ${ranking.totalPlayers}명`)
      expect(wrapper.get('[data-testid="ranking-unit"]').text()).toBe(
        ranking.unit,
      )
      expect(router.currentRoute.value.query.game).toBe(game.id)
    }
  })
})
