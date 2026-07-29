import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import { gameCatalog } from '../mocks/pages'
import GamesPage from './GamesPage.vue'

async function mountGamesPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/games', name: 'games', component: GamesPage },
      {
        path: '/games/:gameId',
        name: 'game-detail',
        component: { template: '<main>게임 상세</main>' },
      },
    ],
  })

  await router.push('/games')
  await router.isReady()

  return {
    router,
    wrapper: mount(GamesPage, { global: { plugins: [router] } }),
  }
}

describe('GamesPage', () => {
  it('renders all five catalog games and opens the rhythm game detail page', async () => {
    const { router, wrapper } = await mountGamesPage()
    const cards = wrapper.findAll('.game-card')
    const rhythmGameIndex = gameCatalog.findIndex(
      (game) => game.id === 'rhythm',
    )

    expect(cards).toHaveLength(5)
    expect(rhythmGameIndex).toBeGreaterThanOrEqual(0)

    await cards[rhythmGameIndex]!.get('button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/games/rhythm')
  })

  it('renders the coming-soon teaser card with all games marked playable', async () => {
    const { wrapper } = await mountGamesPage()

    const comingSoonCard = wrapper.find('.coming-soon-card')
    expect(comingSoonCard.exists()).toBe(true)
    expect(comingSoonCard.text()).toContain('새로운 게임 준비 중!')

    const statuses = wrapper
      .findAll('.game-card__image > span')
      .map((span) => span.text())
    expect(statuses).toHaveLength(gameCatalog.length)
    expect(statuses.every((status) => status === '플레이 가능')).toBe(true)
  })
})
