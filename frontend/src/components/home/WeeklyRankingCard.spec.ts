import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { WeeklyRankingGame } from '../../types/home'
import WeeklyRankingCard from './WeeklyRankingCard.vue'

function buildGame(
  overrides: Partial<WeeklyRankingGame> = {},
): WeeklyRankingGame {
  return {
    id: 'blink',
    title: 'Eye Show Speed (눈 깜빡이기)',
    mode: '1 vs 1',
    image: 'game-blink.png',
    tone: 'purple',
    unit: '회',
    records: [],
    myRank: 0,
    ...overrides,
  }
}

describe('WeeklyRankingCard', () => {
  it('shows 3 placeholder slots and the empty overlay when there are no records yet', () => {
    const wrapper = mount(WeeklyRankingCard, {
      props: { game: buildGame({ records: [] }) },
    })

    const records = wrapper.findAll('.weekly-ranking-card__record')
    expect(records).toHaveLength(3)
    records.forEach((record) => {
      expect(record.find('.weekly-ranking-card__nickname').text()).toBe(
        '순위 없음',
      )
    })
    expect(wrapper.text()).toContain('참여해서 랭커가 되어보세요')
  })

  it('renders only the 1st place slot with a real record when 2nd and 3rd are missing', () => {
    const wrapper = mount(WeeklyRankingCard, {
      props: {
        game: buildGame({
          records: [
            {
              rank: 1,
              value: 128,
              label: '128회',
              nickname: '눈빛왕',
              avatar: 'avatar.png',
            },
          ],
        }),
      },
    })

    const records = wrapper.findAll('.weekly-ranking-card__record')
    expect(records).toHaveLength(3)

    const first = wrapper.find('.weekly-ranking-card__record--1')
    expect(first.find('.weekly-ranking-card__nickname').text()).toBe('눈빛왕')

    const second = wrapper.find('.weekly-ranking-card__record--2')
    const third = wrapper.find('.weekly-ranking-card__record--3')
    expect(second.find('.weekly-ranking-card__nickname').text()).toBe(
      '순위 없음',
    )
    expect(third.find('.weekly-ranking-card__nickname').text()).toBe(
      '순위 없음',
    )

    expect(wrapper.find('.weekly-ranking-card__empty-overlay').exists()).toBe(
      false,
    )
  })
})
