import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'
import WeeklyRankingCard from '../components/home/WeeklyRankingCard.vue'
import { weeklyRankingGames } from '../mocks/home'
import HomePage from './HomePage.vue'

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
  })

  it('keeps five games and shows a three-card desktop viewport', () => {
    const wrapper = mount(HomePage, {
      global: { plugins: [createTestRouter()] },
    })

    expect(weeklyRankingGames).toHaveLength(5)
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
})
