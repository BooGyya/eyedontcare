import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WeeklyRankingCard from '../components/home/WeeklyRankingCard.vue'
import { weeklyRankingGamePresets } from '../mocks/home'
import HomePage from './HomePage.vue'

/** `GET /api/v1/rankings` 요약 응답의 게임 5개 전부를 채운 기본 스텁 데이터. */
const RANKING_SUMMARY_FIXTURE = {
  period: 'weekly',
  weekStart: '2026-08-03',
  games: [
    {
      gameName: 'BLINK',
      rankType: 'BEST_SCORE',
      unit: 'count',
      top: [
        { rank: 1, userId: 1, nickname: '눈빛왕', value: 128 },
        { rank: 2, userId: 2, nickname: '초롱이', value: 116 },
        { rank: 3, userId: 3, nickname: '반짝콩', value: 103 },
      ],
      myRank: null,
    },
    {
      gameName: 'DRAWING',
      rankType: 'BEST_SCORE',
      unit: 'point',
      top: [
        { rank: 1, userId: 4, nickname: '선긋기달인', value: 2450 },
        { rank: 2, userId: 5, nickname: '몽글이', value: 2230 },
        { rank: 3, userId: 6, nickname: '보라콩', value: 1980 },
      ],
      myRank: null,
    },
    {
      gameName: 'EYEFIGHT',
      rankType: 'BEST_SCORE',
      unit: 'second',
      top: [
        { rank: 1, userId: 7, nickname: '집중마스터', value: 87.5 },
        { rank: 2, userId: 8, nickname: '눈동자', value: 79.3 },
        { rank: 3, userId: 9, nickname: '별빛', value: 72.1 },
      ],
      myRank: null,
    },
    {
      gameName: 'RHYTHM',
      rankType: 'BEST_SCORE',
      unit: 'point',
      top: [
        { rank: 1, userId: 10, nickname: '리듬의 별', value: 532 },
        { rank: 2, userId: 11, nickname: '콤보 장인', value: 487 },
        { rank: 3, userId: 12, nickname: '눈빛 비트', value: 421 },
      ],
      myRank: null,
    },
    {
      gameName: 'HOCKEY',
      rankType: 'WIN_COUNT',
      unit: 'win',
      top: [
        { rank: 1, userId: 13, nickname: '바람의 지배자', value: 5260 },
        { rank: 2, userId: 14, nickname: '시선 골키퍼', value: 4980 },
        {
          rank: 3,
          userId: 15,
          nickname: '눈을 건강하게 지키는 플레이어',
          value: 4640,
        },
      ],
      myRank: null,
    },
  ],
}

/** 홈 요약 랭킹 fetch 스텁. 인자를 생략하면 5개 게임이 모두 채워진 기본 데이터를 돌려준다. */
function stubRankingSummaryFetch(data: unknown = RANKING_SUMMARY_FIXTURE) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ code: 'OK', message: '', data }),
    })),
  )
}

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: HomePage },
      { path: '/games', component: { template: '<div />' } },
      { path: '/ranking', component: { template: '<div />' } },
    ],
  })
}

function dispatchPointerEvent(
  element: Element,
  type: string,
  clientX: number,
  clientY = 100,
) {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperties(event, {
    button: { value: 0 },
    clientX: { value: clientX },
    clientY: { value: clientY },
    pointerId: { value: 1 },
    pointerType: { value: 'touch' },
  })
  element.dispatchEvent(event)
}

describe('HomePage weekly ranking carousel', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'innerWidth', {
      configurable: true,
      value: 1200,
    })
    stubRankingSummaryFetch()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('keeps five games and shows a three-card desktop viewport', async () => {
    const wrapper = mount(HomePage, {
      global: { plugins: [createTestRouter()] },
    })
    await flushPromises()

    expect(weeklyRankingGamePresets).toHaveLength(5)
    expect(wrapper.findAllComponents(WeeklyRankingCard)).toHaveLength(5)
    expect(wrapper.find('.weekly-ranking-card__header span').exists()).toBe(
      false,
    )
    expect(wrapper.find('.weekly-ranking-card__actions').exists()).toBe(false)
    expect(wrapper.find('.weekly-ranking__heading a').exists()).toBe(false)
    expect(wrapper.findAll('.weekly-ranking-card a')).toHaveLength(0)
    expect(wrapper.text()).toContain('눈빛왕')
    expect(wrapper.text()).toContain('초롱이')
    expect(wrapper.text()).toContain('반짝콩')
    expect(wrapper.findAll('.weekly-ranking-card__avatar')).toHaveLength(15)
    expect(wrapper.findAll('.weekly-ranking-card__podium')).toHaveLength(15)
    expect(wrapper.findAll('.weekly-ranking-card__score')).toHaveLength(0)
    expect(wrapper.get('.weekly-ranking-card').text()).not.toContain('128회')
    expect(wrapper.get('.weekly-ranking-card').text()).not.toContain('116회')
    expect(wrapper.get('.weekly-ranking-card').text()).not.toContain('103회')
    expect(
      wrapper
        .find('.weekly-ranking-card__record--1 .weekly-ranking-card__podium')
        .attributes('style'),
    ).toContain('--podium-height: 56%')
    expect(
      wrapper
        .find('.weekly-ranking-card__nickname')
        .classes('weekly-ranking-card__nickname'),
    ).toBe(true)
    expect(
      wrapper
        .find('.weekly-ranking-card__record--1 .weekly-ranking-card__player')
        .text()
        .indexOf('눈빛왕'),
    ).toBeGreaterThan(
      wrapper
        .find('.weekly-ranking-card__record--1 .weekly-ranking-card__player')
        .text()
        .indexOf('1'),
    )
    expect(wrapper.get('.weekly-ranking__cards').attributes('style')).toContain(
      '--visible-ranking-count: 3',
    )
    expect(
      wrapper.get('.quick-action-strip__icon--discord').attributes('class'),
    ).toContain('quick-action-strip__icon--discord')
  })

  it('moves one card at a time and disables controls at both ends', async () => {
    const wrapper = mount(HomePage, {
      global: { plugins: [createTestRouter()] },
    })
    const previous = wrapper.get('[aria-label="이전 랭킹 보기"]')
    const next = wrapper.get('[aria-label="다음 랭킹 보기"]')

    expect(previous.attributes()).toHaveProperty('disabled')
    await next.trigger('click')
    expect(wrapper.get('.weekly-ranking__cards').attributes('style')).toContain(
      '--ranking-index: 1',
    )
    await next.trigger('click')
    expect(next.attributes()).toHaveProperty('disabled')
    await previous.trigger('click')
    expect(wrapper.get('.weekly-ranking__cards').attributes('style')).toContain(
      '--ranking-index: 1',
    )
  })

  it('snaps to the nearest card after pointer dragging', async () => {
    const wrapper = mount(HomePage, {
      global: { plugins: [createTestRouter()] },
    })
    const clip = wrapper.get('.weekly-ranking__clip')

    dispatchPointerEvent(clip.element, 'pointerdown', 300)
    dispatchPointerEvent(clip.element, 'pointermove', 100)
    dispatchPointerEvent(clip.element, 'pointerup', 100)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('.weekly-ranking__cards').attributes('style')).toContain(
      '--ranking-index: 1',
    )

    dispatchPointerEvent(clip.element, 'pointerdown', 300)
    dispatchPointerEvent(clip.element, 'pointermove', 240)
    dispatchPointerEvent(clip.element, 'pointerup', 240)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('.weekly-ranking__cards').attributes('style')).toContain(
      '--ranking-index: 1',
    )
  })

  it('prevents native dragging and suppresses the click after a real drag', async () => {
    const wrapper = mount(HomePage, {
      global: { plugins: [createTestRouter()] },
    })
    const clip = wrapper.get('.weekly-ranking__clip')
    const gameImage = wrapper.get('.weekly-ranking-card__header img')

    expect(gameImage.attributes('draggable')).toBe('false')
    expect(
      gameImage.element.dispatchEvent(
        new Event('dragstart', { bubbles: true, cancelable: true }),
      ),
    ).toBe(false)

    dispatchPointerEvent(clip.element, 'pointerdown', 300)
    dispatchPointerEvent(clip.element, 'pointermove', 100)
    dispatchPointerEvent(clip.element, 'pointerup', 100)
    await wrapper.vm.$nextTick()

    const clickAfterDrag = new MouseEvent('click', {
      bubbles: true,
      cancelable: true,
    })
    expect(clip.element.dispatchEvent(clickAfterDrag)).toBe(false)
    expect(clickAfterDrag.defaultPrevented).toBe(true)

    dispatchPointerEvent(clip.element, 'pointerdown', 300)
    dispatchPointerEvent(clip.element, 'pointermove', 296)
    dispatchPointerEvent(clip.element, 'pointerup', 296)
    const regularClick = new MouseEvent('click', {
      bubbles: true,
      cancelable: true,
    })
    expect(clip.element.dispatchEvent(regularClick)).toBe(true)
  })

  it('renders the HTML and CSS hero speech bubble', () => {
    const wrapper = mount(HomePage, {
      global: { plugins: [createTestRouter()] },
    })

    expect(wrapper.get('.hero-banner__bubble').text()).toContain(
      '눈으로 뭐 할래?',
    )
  })

  it('switches hero banner slides with the indicator dots', async () => {
    const wrapper = mount(HomePage, {
      global: { plugins: [createTestRouter()] },
    })
    const indicators = wrapper.findAll('.hero-banner__indicator')

    expect(indicators).toHaveLength(4)
    expect(wrapper.text()).toContain('눈으로 놀고')

    await indicators[1].trigger('click')
    expect(wrapper.text()).toContain('다섯 가지 미니게임')

    await indicators[3].trigger('click')
    expect(wrapper.text()).toContain('랭킹')
    expect(wrapper.text()).toContain('게임 시작하기')
  })

  it('shows a trophy icon in the weekly ranking heading', () => {
    const wrapper = mount(HomePage, {
      global: { plugins: [createTestRouter()] },
    })

    expect(wrapper.get('#weekly-ranking-title').text()).toContain('🏆')
  })

  it('opens discord external URL when quick action is clicked', async () => {
    const openSpy = vi.spyOn(globalThis, 'open').mockImplementation(() => null)
    try {
      const wrapper = mount(HomePage, {
        global: { plugins: [createTestRouter()] },
      })
      const discordButton = wrapper.get('[data-testid="quick-action-discord"]')

      await discordButton.trigger('click')

      expect(openSpy).toHaveBeenCalledWith(
        'https://discord.gg/8SyyCmGRC',
        '_blank',
        'noopener,noreferrer',
      )
    } finally {
      openSpy.mockRestore()
    }
  })

  it('advances the hero carousel automatically and loops', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = mount(HomePage, {
        global: { plugins: [createTestRouter()] },
      })
      const indicators = wrapper.findAll('.hero-banner__indicator')

      expect(indicators[0].classes()).toContain(
        'hero-banner__indicator--active',
      )

      await vi.advanceTimersByTimeAsync(5000)
      expect(indicators[1].classes()).toContain(
        'hero-banner__indicator--active',
      )

      await indicators[3].trigger('click')
      await vi.advanceTimersByTimeAsync(5000)
      expect(indicators[0].classes()).toContain(
        'hero-banner__indicator--active',
      )
    } finally {
      vi.useRealTimers()
    }
  })
})
