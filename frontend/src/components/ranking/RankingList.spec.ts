import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { gameRankings } from '../../mocks/pages'
import { useAuthStore } from '../../stores/auth'
import type { GameRanking } from '../../types/pages'
import RankingList from './RankingList.vue'

function mountRankingList(ranking: GameRanking, authenticated = false) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (authenticated) {
    useAuthStore().setAuthenticatedUser({
      id: 7,
      nickname: '눈썹 최강자',
      level: 12,
      avatar: 'avatar.png',
      email: null,
      loginType: 'LOCAL',
    })
  }

  return mount(RankingList, {
    props: { ranking },
    global: { plugins: [pinia] },
  })
}

describe('RankingList', () => {
  it('renders the podium, general cards, and the highlighted current user', () => {
    const blinkRanking = gameRankings.find(
      (ranking) => ranking.gameId === 'blink',
    )!
    const wrapper = mountRankingList(blinkRanking, true)

    expect(wrapper.get('[data-testid="podium-rank-2"]').text()).toContain('2위')
    expect(wrapper.get('[data-testid="podium-rank-1"]').text()).toContain('1위')
    expect(wrapper.get('[data-testid="podium-rank-3"]').text()).toContain('3위')
    expect(wrapper.get('[data-testid="ranking-row-7"]').classes()).toContain(
      'ranking-list__row--current-user',
    )
    expect(wrapper.get('[data-testid="ranking-row-5"]').text()).toContain(
      '찰나의 눈빛',
    )
    expect(wrapper.get('[data-testid="ranking-row-4"]').text()).not.toContain(
      'Lv. 15',
    )
    expect(wrapper.find('[data-testid="ranking-current-user"]').exists()).toBe(
      false,
    )

    const renderedRanks = wrapper
      .findAll('[data-testid^="podium-rank-"], [data-testid^="ranking-row-"]')
      .map((row) => Number(row.attributes('data-testid')?.match(/(\d+)$/)?.[1]))
    expect(renderedRanks.sort((left, right) => left - right)).toEqual(
      Array.from({ length: 10 }, (_, index) => index + 1),
    )

    const totalScores = [...blinkRanking.players]
      .sort((left, right) => left.rank - right.rank)
      .map((player) => Number.parseInt(player.score.replaceAll(',', ''), 10))
    expect(totalScores).toEqual(
      [...totalScores].sort((left, right) => right - left),
    )
  })

  it('renders game rankings through top 10 and shows a separate rank card for a user outside top 10', () => {
    const holdRanking = gameRankings.find(
      (ranking) => ranking.gameId === 'hold',
    )
    const wrapper = mountRankingList(holdRanking!, true)
    const renderedRanks = wrapper
      .findAll('[data-testid^="podium-rank-"], [data-testid^="ranking-row-"]')
      .map((row) => Number(row.attributes('data-testid')?.match(/(\d+)$/)?.[1]))

    expect(renderedRanks.sort((left, right) => left - right)).toEqual(
      Array.from({ length: 10 }, (_, index) => index + 1),
    )

    const topTenRecords = holdRanking!.players
      .filter((player) => player.rank <= 10)
      .sort((left, right) => left.rank - right.rank)
      .map((player) => Number.parseFloat(player.score))
    expect(topTenRecords).toEqual(
      [...topTenRecords].sort((left, right) => right - left),
    )

    expect(wrapper.find('[data-testid="ranking-row-12"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="ranking-row-4"]').text()).toContain(
      '77.8초',
    )
    expect(
      wrapper.get('[data-testid="ranking-current-user"]').text(),
    ).toContain('12위')
    expect(
      wrapper.get('[data-testid="ranking-current-user"]').text(),
    ).toContain('63.4초')
    expect(
      wrapper.get('[data-testid="ranking-current-user"]').text(),
    ).toContain('눈썹 최강자')
    expect(
      wrapper.get('[data-testid="ranking-current-user"] img').attributes('alt'),
    ).toBe('눈썹 최강자 프로필')
  })

  it('hides personal ranking information for guests', () => {
    const holdRanking = gameRankings.find(
      (ranking) => ranking.gameId === 'hold',
    )!
    const wrapper = mountRankingList(holdRanking)

    expect(wrapper.find('[data-testid="ranking-current-user"]').exists()).toBe(
      false,
    )
    expect(wrapper.get('[data-testid="ranking-row-4"]').text()).not.toContain(
      '눈썹 최강자',
    )
  })

  it('renders ranks 1 through 10 without gaps for every game ranking', () => {
    for (const ranking of gameRankings) {
      const wrapper = mountRankingList(ranking, true)
      const renderedRanks = wrapper
        .findAll('[data-testid^="podium-rank-"], [data-testid^="ranking-row-"]')
        .map((row) =>
          Number(row.attributes('data-testid')?.match(/(\d+)$/)?.[1]),
        )

      expect(renderedRanks.sort((left, right) => left - right)).toEqual(
        Array.from({ length: 10 }, (_, index) => index + 1),
      )
      expect(ranking.sortOrder).toBe('desc')

      const records = [...ranking.players]
        .filter((player) => player.rank <= 10)
        .sort((left, right) => left.rank - right.rank)
        .map((player) => Number.parseFloat(player.score.replaceAll(',', '')))
      expect(records).toEqual([...records].sort((left, right) => right - left))

      if (ranking.myRank <= 10) {
        expect(
          wrapper
            .get(`[data-testid="ranking-row-${ranking.myRank}"]`)
            .classes(),
        ).toContain('ranking-list__row--current-user')
        expect(
          wrapper.find('[data-testid="ranking-current-user"]').exists(),
        ).toBe(false)
      }
    }
  })

  it('renders an empty state when ranking data is unavailable', () => {
    const emptyRanking: GameRanking = {
      gameId: 'air',
      gameName: '준비 중 게임',
      unit: '점',
      sortOrder: 'desc',
      myRank: 0,
      myScore: '0점',
      players: [],
    }
    const wrapper = mountRankingList(emptyRanking)

    expect(wrapper.get('[data-testid="ranking-empty"]').text()).toContain(
      '아직 등록된 랭킹이 없어요.',
    )
  })
})
