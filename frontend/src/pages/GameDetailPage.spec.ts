import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import { gameDetails } from '../mocks/game-details'
import GameDetailPage from './GameDetailPage.vue'
import GameReadyPage from './GameReadyPage.vue'
import GamesPage from './GamesPage.vue'

const routes = [
  { path: '/games', name: 'games', component: GamesPage },
  {
    path: '/games/:gameId',
    name: 'game-detail',
    component: GameDetailPage,
  },
  {
    path: '/games/:gameId/ready',
    name: 'game-ready',
    component: GameReadyPage,
  },
]

describe('GameDetailPage', () => {
  it('renders every game detail from a direct URL', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/air')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    for (const game of Object.values(gameDetails)) {
      await router.push(`/games/${game.id}`)
      await flushPromises()
      expect(wrapper.text()).toContain(game.title)
      expect(wrapper.text()).toContain(game.people)
      expect(wrapper.text()).toContain(game.duration)
      expect(wrapper.findAll('.game-detail-page__steps li')).toHaveLength(
        game.steps.length,
      )
    }
  })

  it('moves from the game list to the selected detail route', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games')
    await router.isReady()
    const wrapper = mount(GamesPage, { global: { plugins: [router] } })

    await wrapper.find('.game-card button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('game-detail')
    expect(router.currentRoute.value.params.gameId).toBe('blink')
  })

  it('moves solo mode selections to the game preparation screen', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/draw')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    await wrapper.find('.game-detail-page__modes button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe(
      '/games/draw/ready?mode=solo',
    )
  })
})
