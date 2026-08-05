import { nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import GamePlayPage from './GamePlayPage.vue'
import GameResultPage from './GameResultPage.vue'
import { recognizeDrawing } from '../api/draw'
import { useLastGameResultStore } from '../stores/lastGameResult'
import type { LastGameOutcome } from '../stores/lastGameResult'
import type { GameDetailId } from '../types/game-detail'

const GUEST_STORAGE_KEY = 'eye-dont-care.guestSessionId'

// 그림그리기 AI 채점은 아직 없는 백엔드 엔드포인트를 호출한다 — 테스트에선 실제 네트워크 대신
// 이 함수를 목업해서 성공/실패 응답을 원하는 대로 만들어낸다.
vi.mock('../api/draw', () => ({
  recognizeDrawing: vi.fn(),
}))

// 테스트 환경(jsdom)에는 실제 카메라/MediaPipe가 없어 useEyeTracking().start()가 항상 실패한다.
// GamePlayPage는 카메라 시작이 성공해야 그 다음(대결 상대와의 게임 세션 소켓 연결 등)으로 진행하므로,
// 카메라·시선 인식 부분만 성공한 것처럼 목업 처리한다 — 이 파일이 마운트하는 모든 게임(blink/hold/
// rhythm)이 각자 자기 인스턴스를 만들지만, 어차피 값 자체는 안 쓰고 "성공 여부"만 필요하다.
vi.mock('../composables/useEyeTracking', () => ({
  useEyeTracking: () => ({
    videoRef: ref(null),
    stream: ref(null),
    isActive: ref(true),
    isLoadingModel: ref(false),
    modelError: ref(null),
    faceDetected: ref(false),
    leftEyeState: ref('NOT_DETECTED'),
    rightEyeState: ref('NOT_DETECTED'),
    combinedState: ref('UNKNOWN'),
    leftRatio: ref(0),
    rightRatio: ref(0),
    confidence: ref(0),
    fps: ref(0),
    rawGaze: ref(null),
    screenGaze: ref(null),
    lastEvent: ref(null),
    eventSequence: ref(0),
    onEyeEvent: () => () => {},
    start: async () => true,
    stop: () => {},
    recordEyeSample: async () => ({ success: true, sampleCount: 10 }),
    resetEyeBaseline: () => {},
    applyEyeProfile: () => {},
    eyeProfile: ref({}),
    beginGazeCalibration: () => {},
    addGazeCalibrationSample: () => true,
    finishGazeCalibration: () => null,
    applyGazeProfile: () => {},
    gazeCalibrationTargets: [],
  }),
}))

/** useWaitingRoomSocket.spec.ts / useGameSessionSocket이 쓰는 것과 동일한 WebSocket mock. */
class MockWebSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3
  static instances: MockWebSocket[] = []

  readyState = MockWebSocket.CONNECTING
  sent: string[] = []
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null

  url: string

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }

  send(data: string): void {
    this.sent.push(data)
  }

  close(): void {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.()
  }

  simulateOpen(): void {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
  }

  simulateMessage(payload: unknown): void {
    this.onmessage?.({ data: JSON.stringify(payload) })
  }
}

function createGameRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/games/:gameId/play',
        name: 'game-play',
        component: GamePlayPage,
      },
      {
        path: '/games/:gameId/result',
        name: 'game-result',
        component: GameResultPage,
      },
    ],
  })
}

function createResultPinia(gameId: GameDetailId, drawRounds = false) {
  const pinia = createPinia()
  const store = useLastGameResultStore(pinia)
  store.set({
    gameId,
    mode: 'solo',
    outcome: 'COMPLETED',
    isNewRecord: drawRounds,
    headline: '게임이 종료되었습니다!',
    summary: drawRounds
      ? '3개 라운드의 그림 인식 결과를 확인해보세요.'
      : '플레이 결과를 확인해보세요.',
    scoreLabel: '최종 점수',
    score: drawRounds ? '680점' : '120점',
    stats: [],
    ...(drawRounds
      ? {
          drawRounds: [
            {
              round: 1,
              difficulty: 'EASY',
              prompt: '안경',
              aiGuess: '안경',
              confidence: 0.8,
              answer: '',
              aiCorrect: true,
              answerCorrect: false,
              success: true,
              baseScore: 100,
              timeBonus: 40,
              confidenceBonus: 40,
              score: 180,
              reason: '테스트 결과',
            },
            {
              round: 2,
              difficulty: 'MEDIUM',
              prompt: '우산',
              aiGuess: '우산',
              confidence: 0.9,
              answer: '',
              aiCorrect: true,
              answerCorrect: false,
              success: true,
              baseScore: 150,
              timeBonus: 50,
              confidenceBonus: 80,
              score: 230,
              reason: '테스트 결과',
            },
            {
              round: 3,
              difficulty: 'HARD',
              prompt: '강아지',
              aiGuess: '강아지',
              confidence: 1,
              answer: '',
              aiCorrect: true,
              answerCorrect: false,
              success: true,
              baseScore: 200,
              timeBonus: 60,
              confidenceBonus: 110,
              score: 270,
              reason: '테스트 결과',
            },
          ],
        }
      : {}),
  })
  return pinia
}

function createDrawResultPinia(
  gameId: GameDetailId,
  mode: 'ai' | 'friends' | 'random' = 'random',
) {
  const pinia = createPinia()
  const store = useLastGameResultStore(pinia)
  store.set({
    gameId,
    mode,
    outcome: 'DRAW',
    opponentNickname: '상대 플레이어',
    headline: 'DRAW',
    summary: '마지막까지 팽팽한 승부였어요!',
    scoreLabel: '최종 점수',
    score: gameId === 'air' ? '3' : '1,860점',
    opponentScore: gameId === 'air' ? '3' : '1,860점',
    stats: [
      {
        label: '최대 콤보',
        value: '20',
        opponentValue: '20',
      },
    ],
  })
  return pinia
}

function createCompetitiveResultPinia(
  gameId: GameDetailId,
  outcome: LastGameOutcome,
  mode: 'friends' | 'random' = 'friends',
) {
  const pinia = createPinia()
  const store = useLastGameResultStore(pinia)
  store.set({
    gameId,
    mode,
    outcome,
    opponentNickname: '상대 플레이어',
    headline: outcome === 'LOSE' ? '아쉽게 졌어요' : '승리했어요!',
    summary: '대결 결과를 확인해 보세요.',
    scoreLabel: '최종 점수',
    score: gameId === 'blink' ? '3' : '1,860점',
    opponentScore: gameId === 'blink' ? '5' : '1,240점',
    stats:
      gameId === 'blink'
        ? []
        : [
            {
              label: '최대 콤보',
              value: '24',
            },
          ],
  })
  return pinia
}

describe('gameplay routes', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.mocked(recognizeDrawing).mockReset()
  })

  it.each(['air', 'hold', 'draw', 'rhythm', 'blink'])(
    'renders the %s play route from mock data',
    async (gameId) => {
      const router = createGameRouter()
      await router.push(`/games/${gameId}/play?mode=solo`)
      await router.isReady()
      const wrapper = mount(GamePlayPage, {
        global: { plugins: [router, createPinia()] },
      })
      expect(wrapper.find('.play-shell').exists()).toBe(true)
      wrapper.unmount()
    },
  )

  it('shows the round score dialog and advances to the next round after AI judging succeeds', async () => {
    vi.mocked(recognizeDrawing).mockResolvedValue({
      label: '하트',
      confidence: 0.8,
      isTarget: true,
      reason: 'AI가 하트 모양을 인식했어요.',
      candidates: [],
    })

    const router = createGameRouter()
    await router.push('/games/draw/play?mode=ai')
    await router.isReady()
    const wrapper = mount(GamePlayPage, {
      attachTo: document.body,
      global: { plugins: [router, createPinia()] },
    })
    await flushPromises() // initDrawGame()의 카메라 시작 등 비동기 초기화 완료 대기

    await wrapper.get('.draw-tools .primary').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('ROUND 1 점수')
    expect(document.body.textContent).toContain('정답입니다!')

    const nextButton =
      document.body.querySelector<HTMLButtonElement>('.dialog-action')
    await nextButton?.click()
    await nextTick()
    expect(wrapper.text()).toContain('Round 2')
    wrapper.unmount()
  })

  it('shows a retryable error(대신 성공으로 조용히 넘어가지 않음) when AI judging fails', async () => {
    vi.mocked(recognizeDrawing).mockRejectedValue(
      new Error('AI 채점 서버에 연결하지 못했어요.'),
    )

    const router = createGameRouter()
    await router.push('/games/draw/play?mode=ai')
    await router.isReady()
    const wrapper = mount(GamePlayPage, {
      global: { plugins: [router, createPinia()] },
    })
    await flushPromises() // initDrawGame()의 카메라 시작 등 비동기 초기화 완료 대기

    await wrapper.get('.draw-tools .primary').trigger('click')
    await flushPromises()

    // 실패했을 때 점수 다이얼로그가 뜨면 안 되고(가짜 성공으로 넘어가지 않음), 라운드는 계속
    // 진행 중 상태로 남아 다시 제출을 시도할 수 있어야 한다.
    expect(wrapper.find('.score-backdrop').exists()).toBe(false)
    expect(wrapper.text()).toContain('AI 채점 서버에 연결하지 못했어요.')
    expect(
      wrapper.get<HTMLButtonElement>('.draw-tools .primary').element.disabled,
    ).toBe(false)
    wrapper.unmount()
  })

  it('shows a leave confirmation dialog before emitting leave', async () => {
    const router = createGameRouter()
    await router.push('/games/air/play?mode=solo')
    await router.isReady()
    const wrapper = mount(GamePlayPage, {
      global: { plugins: [router, createPinia()] },
    })

    await wrapper.get('.play-shell__leave').trigger('click')
    expect(wrapper.find('.play-shell__confirm').exists()).toBe(true)
    expect(wrapper.text()).toContain('게임에서 나가시겠어요?')
    expect(wrapper.text()).toContain('몰수패')

    const continueButton = wrapper
      .findAll('.play-shell__confirm-actions button')
      .find((button) => button.text() === '계속하기')
    await continueButton?.trigger('click')

    expect(wrapper.find('.play-shell__confirm').exists()).toBe(false)
    expect(wrapper.find('.play-shell').exists()).toBe(true)
    expect(router.currentRoute.value.name).toBe('game-play')
    wrapper.unmount()
  })

  it.each(['friends', 'random'])(
    'renders separate rhythm game status panels in %s mode',
    async (mode) => {
      const router = createGameRouter()
      await router.push(`/games/rhythm/play?mode=${mode}`)
      await router.isReady()
      const wrapper = mount(GamePlayPage, {
        global: { plugins: [router, createPinia()] },
      })

      expect(wrapper.findAll('.rhythm-duel-player')).toHaveLength(2)
      expect(wrapper.text()).toContain('상대 게임 화면')
      expect(wrapper.text()).toContain('체력')
      wrapper.unmount()
    },
  )

  it.each(['friends', 'random'])(
    'moves to the result screen and stores the opponent score in %s mode',
    async (mode) => {
      globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
      vi.stubGlobal('WebSocket', MockWebSocket)
      MockWebSocket.instances = []

      const router = createGameRouter()
      await router.push(`/games/rhythm/play?mode=${mode}&roomId=room-1`)
      await router.isReady()
      const pinia = createPinia()
      const wrapper = mount(GamePlayPage, {
        global: { plugins: [router, pinia] },
      })
      await nextTick()

      const ws = MockWebSocket.instances[0]
      expect(ws).toBeTruthy()
      ws.simulateOpen()
      ws.simulateMessage({
        type: 'SESSION_STATE',
        data: {
          roomId: 'room-1',
          gameName: 'RHYTHM',
          participants: [
            {
              participantKey: 'GUEST:guest-1',
              displayName: '내 게스트',
              roomRole: 'HOST',
              slotNo: 1,
              isReady: true,
              calibrationStatus: 'COMPLETED',
              joinedAt: new Date().toISOString(),
            },
            {
              participantKey: 'GUEST:opponent',
              displayName: '상대 게스트',
              roomRole: 'PLAYER',
              slotNo: 2,
              isReady: true,
              calibrationStatus: 'COMPLETED',
              joinedAt: new Date().toISOString(),
            },
          ],
        },
      })
      ws.simulateMessage({
        type: 'PLAYER_EVENT',
        data: {
          participantKey: 'GUEST:opponent',
          eventType: 'RHYTHM_STATE',
          payload: { score: 340, combo: 0, health: 0 },
          occurredAt: new Date().toISOString(),
        },
      })

      await nextTick()
      await new Promise((resolve) => globalThis.setTimeout(resolve, 0))
      expect(router.currentRoute.value.name).toBe('game-result')
      expect(useLastGameResultStore(pinia).current?.opponentNickname).toBe(
        '게스트 플레이어',
      )
      expect(useLastGameResultStore(pinia).current?.opponentScore).toBe('340점')

      wrapper.unmount()
      vi.unstubAllGlobals()
      globalThis.sessionStorage.clear()
    },
  )

  it('shows a registered opponent nickname in the result store', async () => {
    globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
    vi.stubGlobal('WebSocket', MockWebSocket)
    MockWebSocket.instances = []

    const router = createGameRouter()
    await router.push('/games/rhythm/play?mode=friends&roomId=room-1')
    await router.isReady()
    const pinia = createPinia()
    const wrapper = mount(GamePlayPage, {
      global: { plugins: [router, pinia] },
    })
    await nextTick()

    const ws = MockWebSocket.instances[0]
    expect(ws).toBeTruthy()
    ws.simulateOpen()
    ws.simulateMessage({
      type: 'SESSION_STATE',
      data: {
        roomId: 'room-1',
        gameName: 'RHYTHM',
        participants: [
          {
            participantKey: 'GUEST:guest-1',
            displayName: '게스트 플레이어',
            roomRole: 'HOST',
            slotNo: 1,
            isReady: true,
            calibrationStatus: 'COMPLETED',
            joinedAt: new Date().toISOString(),
          },
          {
            participantKey: 'USER:42',
            displayName: '회원 닉네임',
            roomRole: 'PLAYER',
            slotNo: 2,
            isReady: true,
            calibrationStatus: 'COMPLETED',
            joinedAt: new Date().toISOString(),
          },
        ],
      },
    })
    ws.simulateMessage({
      type: 'PLAYER_EVENT',
      data: {
        participantKey: 'USER:42',
        eventType: 'RHYTHM_STATE',
        payload: { score: 340, combo: 0, health: 0 },
        occurredAt: new Date().toISOString(),
      },
    })

    await nextTick()
    await new Promise((resolve) => globalThis.setTimeout(resolve, 0))
    expect(router.currentRoute.value.name).toBe('game-result')
    expect(useLastGameResultStore(pinia).current?.opponentNickname).toBe(
      '회원 닉네임',
    )

    wrapper.unmount()
    vi.unstubAllGlobals()
    globalThis.sessionStorage.clear()
  })

  it.each(['air', 'hold', 'draw', 'rhythm', 'blink'])(
    'renders the %s result route from a real result',
    async (gameId) => {
      const router = createGameRouter()
      await router.push(`/games/${gameId}/result?mode=solo`)
      await router.isReady()
      const pinia = createResultPinia(gameId as GameDetailId, gameId === 'draw')
      const wrapper = mount(GameResultPage, {
        global: { plugins: [router, pinia] },
      })
      expect(wrapper.find('.result-shell').exists()).toBe(true)
      wrapper.unmount()
    },
  )

  it.each(['WIN', 'LOSE', 'DRAW'] as const)(
    'renders the blink %s competitive result with the shared duel UI',
    async (outcome) => {
      const router = createGameRouter()
      await router.push('/games/blink/result?mode=friends')
      await router.isReady()
      const wrapper = mount(GameResultPage, {
        global: {
          plugins: [router, createCompetitiveResultPinia('blink', outcome)],
        },
      })

      expect(wrapper.find('.air-result').exists()).toBe(true)
      expect(wrapper.find('.air-duel-scoreboard').exists()).toBe(true)
      expect(wrapper.find('.blink-result').exists()).toBe(false)
      expect(wrapper.find('.duel-loss').exists()).toBe(false)
      expect(wrapper.text()).toContain('3')
      expect(wrapper.text()).toContain('5')
      expect(wrapper.text()).not.toContain('3회')
      expect(wrapper.text()).not.toContain('5회')
      expect(wrapper.text()).not.toContain('깜빡임 횟수')

      if (outcome === 'WIN') {
        expect(wrapper.text()).toContain('YOU WIN!')
      } else if (outcome === 'LOSE') {
        expect(wrapper.text()).toContain('YOU LOSE...')
        expect(wrapper.text()).not.toContain('YOU WIN!')
      } else {
        expect(wrapper.text()).toContain('DRAW')
        expect(wrapper.text()).not.toContain('YOU WIN!')
      }

      wrapper.unmount()
    },
  )

  it('does not render an unknown competitive result as a win', async () => {
    const router = createGameRouter()
    await router.push('/games/rhythm/result?mode=friends')
    await router.isReady()
    const wrapper = mount(GameResultPage, {
      global: {
        plugins: [router, createCompetitiveResultPinia('rhythm', 'UNKNOWN')],
      },
    })

    expect(wrapper.find('.competitive-pending-result').exists()).toBe(true)
    expect(wrapper.find('.duel-loss').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('YOU WIN!')
    wrapper.unmount()
  })

  it('does not render a fallback result when the result store is empty', async () => {
    const router = createGameRouter()
    await router.push('/games/rhythm/result?mode=solo')
    await router.isReady()
    const wrapper = mount(GameResultPage, {
      global: { plugins: [router, createPinia()] },
    })

    expect(wrapper.find('.result-shell').exists()).toBe(false)
    expect(wrapper.find('.missing').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('1,860점')
    wrapper.unmount()
  })

  it('shows the draw result summary with real round score tooltips', async () => {
    const router = createGameRouter()
    await router.push('/games/draw/result?mode=solo')
    await router.isReady()
    const pinia = createResultPinia('draw', true)
    const wrapper = mount(GameResultPage, {
      global: { plugins: [router, pinia] },
    })

    expect(wrapper.text()).toContain('게임이 종료되었습니다!')
    expect(wrapper.text()).toContain(
      '3개 라운드의 그림 인식 결과를 확인해보세요.',
    )
    expect(wrapper.text()).toContain('최종 총점')
    expect(wrapper.text()).toContain('680점')
    expect(wrapper.text()).toContain('NEW RECORD')
    expect(wrapper.text()).not.toContain('랭킹 반영 예정')

    const roundCards = wrapper.findAll('.draw-round-card')
    expect(roundCards).toHaveLength(3)
    expect(wrapper.text()).toContain('정답')
    expect(wrapper.text()).toContain('쉬움')

    const scoreButton = wrapper.get('.draw-round-card__score')
    const describedBy = scoreButton.attributes('aria-describedby')
    expect(describedBy).toBeTruthy()
    const tooltip = wrapper.find(`#${describedBy}`)
    expect(tooltip.exists()).toBe(true)
    expect(tooltip.text()).toContain('상세 점수')
    expect(tooltip.text()).toContain('시간 보너스')

    expect(wrapper.text()).toContain('랭킹 결과')
    expect(wrapper.text()).toContain('전체 랭킹 보기')
    expect(wrapper.text()).toContain('다시 플레이')

    const gamesButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '게임 목록')
    expect(gamesButton?.exists()).toBe(true)
    wrapper.unmount()
  })

  it.each(['air', 'rhythm', 'blink'])(
    'renders the draw result assets for %s competitive games',
    async (gameId) => {
      const router = createGameRouter()
      await router.push('/games/' + gameId + '/result?mode=random')
      await router.isReady()
      const wrapper = mount(GameResultPage, {
        global: {
          plugins: [router, createDrawResultPinia(gameId as GameDetailId)],
        },
      })

      expect(wrapper.text()).toContain('DRAW')
      expect(wrapper.text()).toContain('팽팽했어요!')
      expect(wrapper.text()).not.toContain('YOU WIN!')
      expect(
        wrapper.get('img[alt="무승부 결과 배너 캐릭터"]').attributes('src'),
      ).toContain('profile-draw-result-banner')
      expect(
        wrapper.get('img[alt="무승부 내 플레이어"]').attributes('src'),
      ).toContain('profile-main-character-versus-draw')
      expect(
        wrapper.get('img[alt="무승부 상대 플레이어"]').attributes('src'),
      ).toContain('profile-rival-character-versus-draw')

      wrapper.unmount()
    },
  )
})
