import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import GameReadyPage from './GameReadyPage.vue'

function createReadyRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/games/:gameId/ready',
        name: 'game-ready',
        component: GameReadyPage,
      },
    ],
  })
}

describe('GameReadyPage', () => {
  it('opens the webcam guide before requesting a camera for a solo room', async () => {
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=solo')
    await router.isReady()

    const wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })

    expect(globalThis.document.body.textContent).toContain(
      '게임 준비를 위해 웹캠을 켜주세요',
    )
    expect(wrapper.text()).toContain('혼자하기 준비방')
    expect(wrapper.findAll('.participant-card')).toHaveLength(1)
    expect(wrapper.find('.room-code').exists()).toBe(false)
    wrapper.unmount()
  })

  it('uses the player role for a friend room entered with a room code', async () => {
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=friends&room=4827&role=player')
    await router.isReady()

    const wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })

    // 입장이 확정되기 전에는 입력한 방 코드를 그대로 노출하지 않는다(유령 방 방지).
    expect(wrapper.find('.room-code').text()).not.toContain('4827')
    expect(wrapper.find('.participant-card--me').text()).toContain('PLAYER')
    expect(wrapper.text()).toContain('친구와 대결 준비방')
    wrapper.unmount()
  })

  it('does not show a room code in a random matching waiting room', async () => {
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=random&room=4827')
    await router.isReady()

    const wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })

    expect(wrapper.find('.room-code').exists()).toBe(false)
    expect(wrapper.findAll('.participant-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('랜덤 매칭 준비방')
    wrapper.unmount()
  })

  it('uses the AI preparation title without a game name', async () => {
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=ai')
    await router.isReady()

    const wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })

    expect(wrapper.text()).toContain('AI 대결 준비방')
    expect(wrapper.text()).not.toContain('눈싸움 AI 준비')
    wrapper.unmount()
  })
})
