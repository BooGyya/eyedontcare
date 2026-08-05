import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RankingPage from './RankingPage.vue'
import { useAuthStore } from '../stores/auth'
import type { GameName } from '../types/waitingRoom'
import type { GameRankingResponse } from '../api/ranking'

const getGameRanking =
  vi.fn<(gameName: GameName) => Promise<GameRankingResponse>>()

// getGameRanking만 가짜로 바꾸고, toGameRanking(순수 변환)은 실제 구현을 그대로 쓴다.
vi.mock('../api/ranking', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/ranking')>()
  return {
    ...actual,
    getGameRanking: (gameName: GameName) => getGameRanking(gameName),
  }
})

function fakeResponse(gameName: GameName): GameRankingResponse {
  return {
    gameName,
    rankType: 'BEST_SCORE',
    unit: 'count',
    period: 'WEEKLY',
    weekStart: '2026-08-03',
    rankings: [
      { rank: 1, userId: 1, nickname: '눈빛왕', value: 128 },
      { rank: 2, userId: 2, nickname: '나', value: 116 },
      { rank: 3, userId: 3, nickname: '반짝콩', value: 103 },
      { rank: 4, userId: 4, nickname: '눈사람', value: 101 },
    ],
    myRank: { rank: 2, value: 116 },
    page: 1,
    size: 10,
    totalElements: 4,
    totalPages: 1,
  }
}

function authenticate() {
  const auth = useAuthStore()
  auth.setAuthenticatedUser({
    id: 2,
    nickname: '나',
    level: 1,
    avatar: '',
    profileImageCode: null,
    email: null,
    loginType: null,
    createdAt: null,
  })
}

async function mountRankingPage(path = '/ranking', { authed = true } = {}) {
  setActivePinia(createPinia())
  if (authed) authenticate()

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/ranking', component: RankingPage }],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(RankingPage, { global: { plugins: [router] } })
  await flushPromises()
  return { router, wrapper }
}

describe('RankingPage', () => {
  beforeEach(() => {
    getGameRanking.mockReset()
    getGameRanking.mockImplementation((gameName) =>
      Promise.resolve(fakeResponse(gameName)),
    )
  })

  it('게스트에게는 API를 부르지 않고 로그인 유도를 보여준다', async () => {
    const { wrapper } = await mountRankingPage('/ranking', { authed: false })

    expect(wrapper.text()).toContain('로그인 후 확인')
    expect(wrapper.find('[data-testid="ranking-game-tabs"]').exists()).toBe(
      false,
    )
    expect(getGameRanking).not.toHaveBeenCalled()
  })

  it('로그인 사용자에게 첫 게임(눈 깜빡이기) 랭킹을 불러와 렌더한다', async () => {
    const { wrapper } = await mountRankingPage()

    expect(getGameRanking).toHaveBeenCalledWith('BLINK')
    expect(wrapper.text()).toContain('Eye Show Speed(눈 깜빡이기)')
    expect(wrapper.text()).toContain('눈 깜빡이기')
    expect(wrapper.get('[data-testid="podium-rank-1"]').text()).toContain('1위')
    expect(wrapper.get('[data-testid="ranking-row-4"]').text()).toContain(
      '눈사람',
    )
  })

  it('탭을 바꾸면 해당 게임 랭킹을 다시 불러오고 쿼리에 반영한다', async () => {
    const { router, wrapper } = await mountRankingPage()

    const gameButtons = wrapper
      .get('[data-testid="ranking-game-tabs"]')
      .findAll('button')
    expect(gameButtons).toHaveLength(5)

    await gameButtons[1].trigger('click')
    await flushPromises()

    expect(getGameRanking).toHaveBeenCalledWith('DRAWING')
    expect(router.currentRoute.value.query.game).toBe('draw')
  })

  it('쿼리의 game으로 초기 게임을 선택한다', async () => {
    await mountRankingPage('/ranking?game=hold')

    expect(getGameRanking).toHaveBeenCalledWith('EYEFIGHT')
  })
})
