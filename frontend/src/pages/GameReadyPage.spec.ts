import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GameReadyPage from './GameReadyPage.vue'

class MockWebSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3
  static instances: MockWebSocket[] = []

  readyState = MockWebSocket.CONNECTING
  closeCalls = 0
  sent: string[] = []
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null

  constructor(readonly url: string) {
    MockWebSocket.instances.push(this)
  }

  send(data: string): void {
    this.sent.push(data)
  }

  close(): void {
    this.closeCalls += 1
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

  simulateUnexpectedClose(): void {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.()
  }
}

const GUEST_STORAGE_KEY = 'eye-dont-care.guestSessionId'
const ACCESS_TOKEN_KEY = 'eye-dont-care.accessToken'

function successResponse(data: unknown = null): Response {
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    json: async () => ({ code: 'SUCCESS', message: '성공', data }),
  } as Response
}

function randomRoomState(
  roomId: string,
  roomStatus: 'WAITING' | 'COUNTDOWN' | 'CLOSED' = 'WAITING',
) {
  return {
    roomId,
    roomType: 'RANDOM',
    gameName: 'EYEFIGHT',
    roomCode: '',
    roomStatus,
    countdownEndsAt: roomStatus === 'COUNTDOWN' ? '2026-08-05T00:00:03Z' : null,
    participants: [
      {
        participantKey: 'GUEST:guest-1',
        displayName: '나',
        roomRole: 'PLAYER',
        slotNo: 1,
        isReady: false,
        calibrationStatus: 'PENDING',
        joinedAt: '2026-08-05T00:00:00Z',
      },
      {
        participantKey: 'GUEST:guest-2',
        displayName: '상대',
        roomRole: 'PLAYER',
        slotNo: 2,
        isReady: false,
        calibrationStatus: 'PENDING',
        joinedAt: '2026-08-05T00:00:00Z',
      },
    ],
    createdAt: '2026-08-05T00:00:00Z',
  }
}

function createReadyRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/games/:gameId/ready',
        name: 'game-ready',
        component: GameReadyPage,
      },
      {
        path: '/games/:gameId',
        name: 'game-detail',
        component: { template: '<div>game detail</div>' },
      },
    ],
  })
}

async function mountRandomReadyPage(roomId = 'room-1') {
  globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
  const router = createReadyRouter()
  await router.push(`/games/hold/ready?mode=random&roomId=${roomId}`)
  await router.isReady()
  const wrapper = mount(GameReadyPage, {
    global: { plugins: [router, createPinia()] },
  })
  return { wrapper, router }
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

describe('GameReadyPage RANDOM rematch lifecycle', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    MockWebSocket.instances = []
    globalThis.localStorage.clear()
    globalThis.sessionStorage.clear()
    vi.stubGlobal('WebSocket', MockWebSocket)
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse()),
    )
  })

  afterEach(() => {
    vi.useRealTimers()
    wrapper?.unmount()
    wrapper = undefined
    globalThis.localStorage.clear()
    globalThis.sessionStorage.clear()
    vi.unstubAllGlobals()
  })

  it('connects Match before WaitingRoom for GUEST and skips Match for INVITE', async () => {
    ;({ wrapper } = await mountRandomReadyPage())

    expect(MockWebSocket.instances.map((socket) => socket.url)).toEqual([
      expect.stringContaining('/ws/match'),
      expect.stringContaining('/ws/waiting-rooms/room-1'),
    ])
    MockWebSocket.instances[0].simulateOpen()
    MockWebSocket.instances[1].simulateOpen()
    expect(JSON.parse(MockWebSocket.instances[0].sent[0])).toEqual({
      type: 'AUTH',
      guestSessionId: 'guest-1',
    })
    expect(JSON.parse(MockWebSocket.instances[1].sent[0])).toEqual({
      type: 'AUTH',
      guestSessionId: 'guest-1',
    })

    wrapper.unmount()
    wrapper = undefined
    MockWebSocket.instances = []
    vi.mocked(globalThis.fetch).mockResolvedValueOnce(
      successResponse({
        roomId: 'invite-room',
        roomType: 'INVITE',
        gameName: 'EYEFIGHT',
        roomCode: '1234',
        roomStatus: 'WAITING',
        participant: randomRoomState('invite-room').participants[0],
      }),
    )
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=friends&role=host')
    await router.isReady()
    wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })
    await flushPromises()

    expect(
      MockWebSocket.instances.some((socket) =>
        socket.url.endsWith('/ws/match'),
      ),
    ).toBe(false)
  })

  it('uses accessToken identity for a logged-in USER', async () => {
    globalThis.sessionStorage.clear()
    const payload = btoa(JSON.stringify({ sub: '7' }))
      .replaceAll('+', '-')
      .replaceAll('/', '_')
      .replaceAll('=', '')
    const accessToken = `header.${payload}.signature`
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=random&roomId=room-1')
    await router.isReady()
    wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })
    MockWebSocket.instances[0].simulateOpen()
    MockWebSocket.instances[1].simulateOpen()

    expect(JSON.parse(MockWebSocket.instances[0].sent[0])).toEqual({
      type: 'AUTH',
      accessToken,
    })
    expect(JSON.parse(MockWebSocket.instances[1].sent[0])).toEqual({
      type: 'AUTH',
      accessToken,
    })
  })

  it('does not enter REMATCHING from MATCH_REQUEUED alone and identifies the RANDOM opponent by participantKey', async () => {
    ;({ wrapper } = await mountRandomReadyPage())
    const matchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    const state = randomRoomState('room-1')
    state.participants[1].isReady = true
    waitingSocket.simulateMessage({ type: 'ROOM_STATE', data: state })
    matchSocket.simulateMessage({
      type: 'MATCH_REQUEUED',
      gameType: 'EYEFIGHT',
    })
    await flushPromises()

    expect(wrapper.find('.random-rematch-status').exists()).toBe(false)
    expect(
      wrapper
        .find('.participant-card--opponent')
        .find('.complete-badge')
        .exists(),
    ).toBe(true)
    expect(wrapper.findAll('.participant-card')).toHaveLength(2)
  })

  it('moves only current CLOSED to REMATCHING, cancels countdown, and keeps Match open', async () => {
    vi.useFakeTimers()
    ;({ wrapper } = await mountRandomReadyPage())
    const matchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('old-room', 'CLOSED'),
    })
    expect(wrapper.find('.random-rematch-status').exists()).toBe(false)

    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'COUNTDOWN'),
    })
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })
    await flushPromises()

    expect(wrapper.text()).toContain('상대방이 대기방을 나갔어요.')
    expect(wrapper.text()).toContain('새로운 상대를 찾고 있어요.')
    expect(wrapper.find('.participant-grid').exists()).toBe(false)
    expect(matchSocket.readyState).toBe(MockWebSocket.OPEN)
    expect(waitingSocket.readyState).toBe(MockWebSocket.OPEN)
    expect(waitingSocket.closeCalls).toBe(0)
  })

  it('keeps REMATCHING when the retired WaitingRoom socket closes from the server', async () => {
    const mounted = await mountRandomReadyPage()
    wrapper = mounted.wrapper
    const matchSocket = MockWebSocket.instances[0]
    const oldWaitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    oldWaitingSocket.simulateOpen()
    oldWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })
    oldWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'WAITING'),
    })
    oldWaitingSocket.simulateMessage({
      type: 'GAME_START',
      data: {
        roomId: 'room-1',
        gameName: 'EYEFIGHT',
        startedAt: '2026-08-05T00:00:03Z',
        openviduUrl: null,
        token: null,
      },
    })
    oldWaitingSocket.simulateUnexpectedClose()
    await flushPromises()

    expect(wrapper.text()).toContain('새로운 상대를 찾고 있어요.')
    expect(wrapper.text()).not.toContain(
      '대기방 연결이 예기치 않게 종료됐어요.',
    )
    expect(mounted.router.currentRoute.value.name).toBe('game-ready')
    expect(matchSocket.readyState).toBe(MockWebSocket.OPEN)
  })

  it('adopts the newest room when another MATCH_SUCCESS arrives during a room switch', async () => {
    const mounted = await mountRandomReadyPage()
    wrapper = mounted.wrapper
    const matchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })

    matchSocket.simulateMessage({
      type: 'MATCH_SUCCESS',
      roomId: 'room-a',
      gameType: 'EYEFIGHT',
    })
    matchSocket.simulateMessage({
      type: 'MATCH_SUCCESS',
      roomId: 'room-b',
      gameType: 'EYEFIGHT',
    })
    await flushPromises()

    expect(mounted.router.currentRoute.value.query.roomId).toBe('room-b')
    expect(
      MockWebSocket.instances.filter((socket) =>
        socket.url.includes('/ws/waiting-rooms/room-a'),
      ),
    ).toHaveLength(0)
    expect(
      MockWebSocket.instances.filter((socket) =>
        socket.url.includes('/ws/waiting-rooms/room-b'),
      ),
    ).toHaveLength(1)
  })

  it('replaces the route, connects the new room, and ignores stale room events and GAME_START', async () => {
    const mounted = await mountRandomReadyPage()
    wrapper = mounted.wrapper
    const router = mounted.router
    const matchSocket = MockWebSocket.instances[0]
    const oldWaitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    oldWaitingSocket.simulateOpen()
    const staleMessage = oldWaitingSocket.onmessage
    const staleClose = oldWaitingSocket.onclose
    const staleError = oldWaitingSocket.onerror
    oldWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'WAITING'),
    })
    oldWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })
    matchSocket.simulateMessage({
      type: 'MATCH_REQUEUED',
      gameType: 'EYEFIGHT',
    })
    expect(oldWaitingSocket.closeCalls).toBe(0)
    expect(oldWaitingSocket.readyState).toBe(MockWebSocket.OPEN)
    matchSocket.simulateMessage({
      type: 'MATCH_SUCCESS',
      roomId: 'room-2',
      gameType: 'EYEFIGHT',
    })
    await flushPromises()

    expect(router.currentRoute.value.query.roomId).toBe('room-2')
    expect(oldWaitingSocket.closeCalls).toBe(1)
    const newWaitingSocket = MockWebSocket.instances[2]
    expect(newWaitingSocket.url).toContain('/ws/waiting-rooms/room-2')

    staleMessage?.({
      data: JSON.stringify({
        type: 'GAME_START',
        data: {
          roomId: 'room-1',
          gameName: 'EYEFIGHT',
          startedAt: '2026-08-05T00:00:03Z',
          openviduUrl: null,
          token: null,
        },
      }),
    })
    expect(router.currentRoute.value.name).toBe('game-ready')

    newWaitingSocket.simulateOpen()
    newWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-2', 'WAITING'),
    })
    staleError?.()
    staleClose?.()
    await flushPromises()
    expect(wrapper.find('.random-rematch-status').exists()).toBe(false)
    expect(wrapper.findAll('.participant-card')).toHaveLength(2)
  })

  it('keeps completed local calibration, resends it once, and does not auto-ready in the new room', async () => {
    ;({ wrapper } = await mountRandomReadyPage())
    const matchSocket = MockWebSocket.instances[0]
    const oldWaitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    oldWaitingSocket.simulateOpen()
    const cameraStream = {
      getTracks: () => [],
      getVideoTracks: () => [],
    } as unknown as MediaStream
    const readyPage = wrapper.vm as unknown as {
      cameraStream: MediaStream | null
      isCalibrated: boolean
    }
    readyPage.cameraStream = cameraStream
    readyPage.isCalibrated = true
    const initialState = randomRoomState('room-1')
    initialState.participants[0].calibrationStatus = 'COMPLETED'
    oldWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: initialState,
    })
    oldWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })
    matchSocket.simulateMessage({
      type: 'MATCH_SUCCESS',
      roomId: 'room-2',
      gameType: 'EYEFIGHT',
    })
    await flushPromises()
    const newWaitingSocket = MockWebSocket.instances[2]
    newWaitingSocket.simulateOpen()
    newWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-2', 'WAITING'),
    })
    await flushPromises()

    const commands = newWaitingSocket.sent
      .slice(1)
      .map((raw) => JSON.parse(raw))
    expect(commands).toEqual([
      { type: 'CALIBRATION_STATUS', calibrationStatus: 'IN_PROGRESS' },
      { type: 'CALIBRATION_STATUS', calibrationStatus: 'COMPLETED' },
    ])
    expect(commands).not.toContainEqual({
      type: 'READY_STATUS',
      isReady: true,
    })
    expect(wrapper.text()).toContain('캘리브레이션')
    expect(wrapper.text()).toContain('완료')
    expect(readyPage.cameraStream).toStrictEqual(cameraStream)
  })

  it('treats WaitingRoom close without CLOSED and Match close during REMATCHING as ERROR', async () => {
    ;({ wrapper } = await mountRandomReadyPage())
    const matchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'WAITING'),
    })
    waitingSocket.simulateUnexpectedClose()
    await flushPromises()
    expect(wrapper.text()).toContain('대기방 연결이 예기치 않게 종료됐어요.')

    wrapper.unmount()
    wrapper = undefined
    MockWebSocket.instances = []
    ;({ wrapper } = await mountRandomReadyPage())
    const rematchSocket = MockWebSocket.instances[0]
    const rematchWaitingSocket = MockWebSocket.instances[1]
    rematchSocket.simulateOpen()
    rematchWaitingSocket.simulateOpen()
    rematchWaitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })
    rematchSocket.simulateUnexpectedClose()
    await flushPromises()
    expect(wrapper.text()).toContain('재매칭 연결이 끊어졌어요.')
    expect(wrapper.find('.room-join-error__retry').text()).toBe('다시 찾기')
  })

  it('limits an unexpected Match reconnect to once for the active room', async () => {
    vi.useFakeTimers()
    ;({ wrapper } = await mountRandomReadyPage())
    const initialMatchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    initialMatchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'WAITING'),
    })

    initialMatchSocket.simulateUnexpectedClose()
    const retriedMatchSocket = MockWebSocket.instances[2]
    expect(retriedMatchSocket.url).toContain('/ws/match')
    retriedMatchSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'WAITING'),
    })
    retriedMatchSocket.simulateUnexpectedClose()
    await flushPromises()

    expect(MockWebSocket.instances).toHaveLength(3)
    expect(wrapper.text()).toContain('매칭 서버와의 연결이 끊어졌어요.')
  })

  it('runs retry join once even when the retry button is clicked repeatedly', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)
    fetchMock.mockResolvedValue(
      successResponse({
        participantKey: 'GUEST:guest-1',
        gameType: 'EYEFIGHT',
        matchStatus: 'SEARCHING',
        waitingRoomId: null,
        queuedAt: '2026-08-05T00:00:04Z',
      }),
    )
    ;({ wrapper } = await mountRandomReadyPage())
    const matchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })
    matchSocket.simulateUnexpectedClose()
    await flushPromises()

    const retryButton = wrapper.find('.room-join-error__retry')
    await retryButton.trigger('click')
    await retryButton.trigger('click')
    const retrySocket = MockWebSocket.instances[2]
    retrySocket.simulateOpen()
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/match/join',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(wrapper.text()).toContain('새로운 상대를 찾고 있어요.')
  })

  it('uses WaitingRoom leave for an active room and matchmaking cancel while REMATCHING', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)
    ;({ wrapper } = await mountRandomReadyPage())
    const matchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'WAITING'),
    })
    await wrapper.find('.room-header__actions > button').trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/waiting-rooms/room-1/leave',
      expect.objectContaining({ method: 'POST' }),
    )

    wrapper.unmount()
    wrapper = undefined
    MockWebSocket.instances = []
    fetchMock.mockClear()
    ;({ wrapper } = await mountRandomReadyPage())
    MockWebSocket.instances[0].simulateOpen()
    MockWebSocket.instances[1].simulateOpen()
    MockWebSocket.instances[1].simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })
    await flushPromises()
    await wrapper.find('.room-join-error__back').trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/match/cancel',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('waits for the active WaitingRoom leave response before closing sockets', async () => {
    let resolveLeave: ((response: Response) => void) | undefined
    const fetchMock = vi.mocked(globalThis.fetch)
    fetchMock.mockImplementation(async (url) => {
      if (String(url).endsWith('/waiting-rooms/room-1/leave'))
        return new Promise<Response>((resolve) => {
          resolveLeave = resolve
        })
      return successResponse()
    })
    ;({ wrapper } = await mountRandomReadyPage())
    const matchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'WAITING'),
    })

    await wrapper.find('.room-header__actions > button').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/waiting-rooms/room-1/leave',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(waitingSocket.closeCalls).toBe(0)
    expect(matchSocket.closeCalls).toBe(0)

    resolveLeave?.(successResponse())
    await flushPromises()
    expect(waitingSocket.closeCalls).toBe(1)
    expect(matchSocket.closeCalls).toBe(1)
  })

  it('does not navigate to a late MATCH_SUCCESS while leaving and best-effort leaves that room', async () => {
    let resolveCancel: ((response: Response) => void) | undefined
    const fetchMock = vi.mocked(globalThis.fetch)
    fetchMock.mockImplementation(async (url) => {
      if (String(url).endsWith('/match/cancel'))
        return new Promise<Response>((resolve) => {
          resolveCancel = resolve
        })
      return successResponse()
    })
    const mounted = await mountRandomReadyPage()
    wrapper = mounted.wrapper
    const router = mounted.router
    const matchSocket = MockWebSocket.instances[0]
    const waitingSocket = MockWebSocket.instances[1]
    matchSocket.simulateOpen()
    waitingSocket.simulateOpen()
    waitingSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: randomRoomState('room-1', 'CLOSED'),
    })
    await flushPromises()

    await wrapper.find('.room-join-error__back').trigger('click')
    expect(waitingSocket.closeCalls).toBe(0)
    expect(matchSocket.closeCalls).toBe(0)
    matchSocket.simulateMessage({
      type: 'MATCH_SUCCESS',
      roomId: 'late-room',
      gameType: 'EYEFIGHT',
    })
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('game-ready')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/waiting-rooms/late-room/leave',
      expect.objectContaining({ method: 'POST' }),
    )
    resolveCancel?.(successResponse())
    await flushPromises()
    expect(waitingSocket.closeCalls).toBe(1)
    expect(matchSocket.closeCalls).toBe(1)
    expect(router.currentRoute.value.name).toBe('game-detail')
  })
})
