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
      const displayTitle = game.title.replace(/\s*\([^)]*\)\s*$/, '')
      expect(wrapper.text()).toContain(displayTitle)
      expect(wrapper.text()).toContain(game.people)
      expect(wrapper.text()).toContain(game.duration)

      await wrapper
        .find('.game-detail-page__description-button')
        .trigger('click')
      const expectedSteps = game.guide?.stepIcons?.length
        ? game.steps.length
        : 0
      expect(wrapper.findAll('.game-detail-page__steps li')).toHaveLength(
        expectedSteps,
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

  it('opens the 게임 설명 dialog and closes it with the close button and backdrop', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/blink')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    expect(wrapper.find('.game-detail-page__dialog').exists()).toBe(false)

    await wrapper.find('.game-detail-page__description-button').trigger('click')
    expect(wrapper.find('.game-detail-page__dialog').exists()).toBe(true)

    await wrapper.find('.game-detail-page__dialog-close').trigger('click')
    expect(wrapper.find('.game-detail-page__dialog').exists()).toBe(false)

    await wrapper.find('.game-detail-page__description-button').trigger('click')
    await wrapper.find('.game-detail-page__dialog-backdrop').trigger('click')
    expect(wrapper.find('.game-detail-page__dialog').exists()).toBe(false)
  })

  it('renders the rich 게임 설명 layout for games with a guide and closes with the X button', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/blink')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })
    const guide = gameDetails.blink.guide!

    await wrapper.find('.game-detail-page__description-button').trigger('click')

    expect(wrapper.find('.game-detail-page__guide-title').text()).toBe(
      '게임 설명',
    )
    expect(wrapper.findAll('.game-detail-page__guide-card')).toHaveLength(
      guide.cards!.length,
    )
    expect(wrapper.findAll('.game-detail-page__guide-event')).toHaveLength(
      guide.events!.length,
    )
    expect(wrapper.text()).toContain('혼자하기 랭킹 반영')
    expect(wrapper.text()).toContain('성공 시 보너스 획득')
    expect(wrapper.text()).toContain('이벤트 예시')

    await wrapper.find('.game-detail-page__dialog-x').trigger('click')
    expect(wrapper.find('.game-detail-page__dialog').exists()).toBe(false)
  })

  it('renders the rich guide dialog for every game', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    for (const game of Object.values(gameDetails)) {
      await router.push(`/games/${game.id}`)
      await flushPromises()

      await wrapper
        .find('.game-detail-page__description-button')
        .trigger('click')

      expect(wrapper.find('.game-detail-page__guide-title').exists()).toBe(true)
      expect(wrapper.findAll('.game-detail-page__guide-card')).toHaveLength(
        game.guide!.cards?.length ?? 0,
      )

      await wrapper.find('.game-detail-page__dialog-x').trigger('click')
    }
  })

  it('renders the highlight strip without 게임 방법 for the hold game', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/hold')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    await wrapper.find('.game-detail-page__description-button').trigger('click')

    expect(wrapper.findAll('.game-detail-page__guide-highlight')).toHaveLength(
      gameDetails.hold.guide!.highlights!.length,
    )
    expect(wrapper.find('.game-detail-page__guide-bottom').exists()).toBe(false)
    expect(wrapper.find('.game-detail-page__guide-cards').exists()).toBe(false)
  })

  it('shows the custom duration label for the hold game', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/hold')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    expect(wrapper.text()).toContain('예상 시간')
    expect(wrapper.text()).toContain('30초')

    await router.push('/games/blink')
    await flushPromises()

    expect(wrapper.text()).toContain('제한 시간')
  })

  it('applies the compact modes layout once a game has 4+ modes', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    // 눈싸움은 AI 난이도 대결이 추가되며 모드가 4개(solo/ai/friends/random)가 됐다.
    await router.push('/games/hold')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    expect(wrapper.find('.game-detail-page__modes--compact').exists()).toBe(
      true,
    )

    await router.push('/games/blink')
    await flushPromises()

    expect(wrapper.find('.game-detail-page__modes--compact').exists()).toBe(
      false,
    )
  })

  it('opens an AI difficulty picker for the hold game instead of navigating immediately', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/hold')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    await wrapper.find('.game-detail-page__mode--ai').trigger('click')
    await flushPromises()

    // 난이도를 고르기 전까지는 아직 game-detail에 머물러 있어야 한다.
    expect(router.currentRoute.value.name).toBe('game-detail')
    const options = wrapper.findAll('.game-detail-page__difficulty-option')
    expect(options).toHaveLength(3)

    await wrapper
      .find('.game-detail-page__difficulty-option--hard')
      .trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe(
      '/games/hold/ready?mode=ai&difficulty=hard',
    )
  })

  it('renders the highlight strip without 게임 방법 for the air game', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/air')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    await wrapper.find('.game-detail-page__description-button').trigger('click')

    expect(wrapper.findAll('.game-detail-page__guide-highlight')).toHaveLength(
      gameDetails.air.guide!.highlights!.length,
    )
    expect(wrapper.find('.game-detail-page__guide-bottom').exists()).toBe(false)
    expect(wrapper.find('.game-detail-page__guide-cards').exists()).toBe(false)
  })

  it('renders the 점수 계산 formula panel for the rhythm game', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/rhythm')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    await wrapper.find('.game-detail-page__description-button').trigger('click')

    expect(wrapper.find('.game-detail-page__guide-formula h3').text()).toBe(
      '점수 계산',
    )
    expect(
      wrapper.findAll('.game-detail-page__guide-formula-part'),
    ).toHaveLength(3)
    expect(wrapper.text()).toContain('총 점수')
    expect(
      wrapper.find('.game-detail-page__guide-bottom--single').exists(),
    ).toBe(false)
  })

  it('renders the AI 채점 방식 notes panel for the draw game', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/draw')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })
    const guide = gameDetails.draw.guide!

    await wrapper.find('.game-detail-page__description-button').trigger('click')

    const notes = wrapper.find('.game-detail-page__guide-notes')
    expect(notes.exists()).toBe(true)
    expect(notes.find('h3').text()).toBe('AI 채점 방식')
    expect(notes.findAll('li')).toHaveLength(guide.notes!.items.length)
    expect(
      wrapper.find('.game-detail-page__guide-bottom--single').exists(),
    ).toBe(false)
  })

  it('shows the ranking badge only on ranking-eligible modes', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/blink')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    const soloBadge = wrapper.find(
      '.game-detail-page__mode--solo .game-detail-page__mode-badge',
    )
    expect(soloBadge.text()).toContain('랭킹에 반영')
    expect(
      wrapper
        .find('.game-detail-page__mode--friends .game-detail-page__mode-badge')
        .exists(),
    ).toBe(false)
    expect(
      wrapper
        .find('.game-detail-page__mode--random .game-detail-page__mode-badge')
        .exists(),
    ).toBe(false)
  })

  it('shows the AI robot art on ai modes', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/draw')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    expect(wrapper.find('.game-detail-page__mode-robot').exists()).toBe(true)

    await router.push('/games/blink')
    await flushPromises()

    expect(wrapper.find('.game-detail-page__mode-robot').exists()).toBe(false)
  })

  it('moves mode selections to the game preparation screen', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/draw')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    await wrapper.find('.game-detail-page__modes button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/games/draw/ready?mode=ai')
  })
  
  it('opens the room dialog for friends mode without requiring login', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/games/blink')
    await router.isReady()
    const wrapper = mount(GameDetailPage, {
      global: { plugins: [createPinia(), router] },
    })

    await wrapper.find('.game-detail-page__mode--friends').trigger('click')

    expect(document.querySelector('.game-room-dialog')).toBeTruthy()
  })
})
