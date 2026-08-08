import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GameReadyPage from './GameReadyPage.vue'
import { useToast } from '../composables/useToast'
import { SOLO_PLAY_ENTRY_KEY } from '../utils/soloPlayEntry'

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
  url: string

  constructor(url: string) {
    // 파라미터 프로퍼티(readonly url)는 erasableSyntaxOnly에서 금지돼 prod 빌드가 실패한다.
    // 명시 필드 + 대입으로 바꿔 지울 수 있는 문법만 사용한다.
    this.url = url
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

function errorResponse(code: string, message: string, status = 400): Response {
  return {
    ok: false,
    status,
    statusText: 'Error',
    json: async () => ({ code, message, data: null }),
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

function inviteRoomState(
  roomId = 'invite-room',
  roomStatus: 'WAITING' | 'COUNTDOWN' | 'CLOSED' = 'WAITING',
  participants = [
    {
      participantKey: 'GUEST:guest-1',
      displayName: '나',
      roomRole: 'HOST' as const,
      slotNo: 1,
      isReady: false,
      calibrationStatus: 'PENDING' as const,
      joinedAt: '2026-08-05T00:00:00Z',
    },
    {
      participantKey: 'GUEST:guest-2',
      displayName: '상대',
      roomRole: 'PLAYER' as const,
      slotNo: 2,
      isReady: false,
      calibrationStatus: 'PENDING' as const,
      joinedAt: '2026-08-05T00:00:00Z',
    },
  ],
) {
  return {
    roomId,
    roomType: 'INVITE' as const,
    gameName: 'EYEFIGHT' as const,
    roomCode: '4827',
    roomStatus,
    countdownEndsAt: roomStatus === 'COUNTDOWN' ? '2026-08-05T00:00:03Z' : null,
    participants,
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
      {
        path: '/games/:gameId/play',
        name: 'game-play',
        component: { template: '<div>game play</div>' },
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

function inviteRestResponse(
  roomRole: 'HOST' | 'PLAYER',
  roomId = 'invite-room',
) {
  const state = inviteRoomState(roomId)
  const ownParticipant = {
    ...state.participants[0],
    roomRole,
  }
  return {
    roomId,
    roomType: 'INVITE',
    gameName: 'EYEFIGHT',
    roomCode: '4827',
    roomStatus: 'WAITING',
    participant: ownParticipant,
    participants: [ownParticipant, state.participants[1]],
    createdAt: state.createdAt,
    guestSessionId: 'guest-1',
  }
}

async function mountInviteReadyPage(path: string) {
  const router = createReadyRouter()
  await router.push(path)
  await router.isReady()
  const wrapper = mount(GameReadyPage, {
    global: { plugins: [router, createPinia()] },
  })
  await flushPromises()
  return { wrapper, router }
}

interface InviteReadyPageTestVm {
  permissionStatus: 'idle' | 'requesting' | 'granted' | 'denied' | 'unavailable'
  isCalibrationOpen: boolean
  isCalibrated: boolean
  notifyCalibrationStarted: () => void
  finishCalibration: () => void
  markPlayerReady: () => void
  handleCalibrationBack: () => void
}

function inviteReadyPageVm(wrapper: VueWrapper): InviteReadyPageTestVm {
  return wrapper.vm as unknown as InviteReadyPageTestVm
}

describe('GameReadyPage', () => {
  it.each(['rhythm', 'hold'])(
    'issues a SOLO entry ticket immediately before %s play navigation',
    async (gameId) => {
      globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
      vi.useFakeTimers()
      const router = createReadyRouter()
      await router.push(`/games/${gameId}/ready?mode=solo`)
      await router.isReady()

      const wrapper = mount(GameReadyPage, {
        global: { plugins: [router, createPinia()] },
      })
      const readyPage = wrapper.vm as unknown as {
        openGameStartDialog: () => void
      }

      readyPage.openGameStartDialog()
      if (gameId === 'hold') await vi.advanceTimersByTimeAsync(3000)
      await flushPromises()

      expect(router.currentRoute.value.name).toBe('game-play')
      expect(
        JSON.parse(
          globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY) ?? '',
        ),
      ).toMatchObject({ gameId, mode: 'solo' })
      wrapper.unmount()
      vi.useRealTimers()
      globalThis.sessionStorage.clear()
    },
  )

  it.each(['solo', 'ai'])(
    'does not issue a %s ticket when calibration only completes',
    async (mode) => {
      globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
      const router = createReadyRouter()
      await router.push(`/games/hold/ready?mode=${mode}`)
      await router.isReady()

      const wrapper = mount(GameReadyPage, {
        global: { plugins: [router, createPinia()] },
      })
      const readyPage = wrapper.vm as unknown as {
        finishCalibration: () => void
      }

      readyPage.finishCalibration()

      expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
      wrapper.unmount()
      globalThis.sessionStorage.clear()
    },
  )

  it.each(['rhythm', 'hold'])(
    'issues an AI ticket only when %s actually navigates to play',
    async (gameId) => {
      globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
      vi.useFakeTimers()
      const router = createReadyRouter()
      await router.push(`/games/${gameId}/ready?mode=ai`)
      await router.isReady()

      const wrapper = mount(GameReadyPage, {
        global: { plugins: [router, createPinia()] },
      })
      const readyPage = wrapper.vm as unknown as {
        finishCalibration: () => void
        markPlayerReady: () => void
      }

      readyPage.finishCalibration()
      expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()

      readyPage.markPlayerReady()
      if (gameId === 'hold') {
        expect(
          globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY),
        ).toBeNull()
        await vi.advanceTimersByTimeAsync(3000)
      }
      await flushPromises()

      expect(router.currentRoute.value.name).toBe('game-play')
      expect(
        JSON.parse(
          globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY) ?? '',
        ),
      ).toMatchObject({ gameId, mode: 'ai' })
      wrapper.unmount()
      vi.useRealTimers()
      globalThis.sessionStorage.clear()
    },
  )

  it('does not issue an AI ticket for camera, calibration start, or cancellation', async () => {
    globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=ai')
    await router.isReady()

    const wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })
    const readyPage = wrapper.vm as unknown as {
      permissionStatus: 'granted'
      isCalibrationOpen: boolean
      notifyCalibrationStarted: () => void
      handleCalibrationBack: () => void
    }

    readyPage.permissionStatus = 'granted'
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()

    readyPage.notifyCalibrationStarted()
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()

    readyPage.isCalibrationOpen = true
    readyPage.handleCalibrationBack()

    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
    wrapper.unmount()
    globalThis.sessionStorage.clear()
  })

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

  it('does not show a room code in a random matching waiting room', async () => {
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=random&room=4827')
    await router.isReady()

    const wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })

    expect(wrapper.find('.room-code').exists()).toBe(false)
    expect(wrapper.findAll('.participant-card')).toHaveLength(2)
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
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

describe('GameReadyPage INVITE lifecycle', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    MockWebSocket.instances = []
    globalThis.localStorage.clear()
    globalThis.sessionStorage.clear()
    // 실제 앱은 main.ts가 부팅 시 신원을 확보한 뒤에야 화면이 뜬다. 페이지만 따로 마운트하는
    // 이 테스트에서도 같은 상태를 만들어 준다 — 안 그러면 준비방의 방어적 ensureIdentity()가
    // 게스트 세션을 발급하면서 아래 fetch 호출 순서 검증을 어긋나게 한다.
    globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
    useToast().hideToast()
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    globalThis.localStorage.clear()
    globalThis.sessionStorage.clear()
    useToast().hideToast()
    vi.unstubAllGlobals()
  })

  it('joins by room code, removes role query, and gates preparation until a validated server PLAYER snapshot', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('PLAYER'))),
    )
    const mounted = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends&room=4827&role=host',
    )
    wrapper = mounted.wrapper

    const fetchCall = vi.mocked(globalThis.fetch).mock.calls[0]
    expect(String(fetchCall?.[0])).toContain('/waiting-rooms/join')
    expect(JSON.parse(String(fetchCall?.[1]?.body))).toEqual({
      roomCode: '4827',
    })
    expect(mounted.router.currentRoute.value.query.role).toBeUndefined()
    expect(wrapper.find('.invite-entry-status').text()).toContain(
      '대기방 연결을 확인하고 있어요.',
    )
    expect(wrapper.find('.participant-grid').exists()).toBe(false)
    expect(wrapper.find('.room-code').exists()).toBe(false)
    expect(globalThis.document.body.textContent).not.toContain(
      '게임 준비를 위해 웹캠을 켜주세요',
    )

    const socket = MockWebSocket.instances[0]
    const state = inviteRoomState('invite-room', 'WAITING', [
      {
        ...inviteRoomState().participants[1],
        roomRole: 'HOST',
      },
      {
        ...inviteRoomState().participants[0],
        roomRole: 'PLAYER',
      },
    ])
    socket.simulateMessage({ type: 'ROOM_STATE', data: state })
    await flushPromises()

    expect(wrapper.findAll('.participant-card')).toHaveLength(2)
    expect(wrapper.find('.participant-card--me').text()).toContain('PLAYER')
    expect(wrapper.find('.participant-card--opponent').text()).toContain('HOST')
    expect(wrapper.find('.room-code__copy').exists()).toBe(false)
    expect(globalThis.document.body.textContent).toContain(
      '게임 준비를 위해 웹캠을 켜주세요',
    )
  })

  it('creates without a fake code and exposes HOST UI only after the server assigns HOST', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    const mounted = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends&role=player',
    )
    wrapper = mounted.wrapper

    const fetchCall = vi.mocked(globalThis.fetch).mock.calls[0]
    expect(String(fetchCall?.[0])).toMatch(/\/waiting-rooms$/)
    expect(JSON.parse(String(fetchCall?.[1]?.body))).toEqual({
      gameName: 'EYEFIGHT',
    })
    expect(mounted.router.currentRoute.value.query).toEqual({
      mode: 'friends',
      room: '4827',
    })
    expect(wrapper.find('.room-code__copy').exists()).toBe(false)

    MockWebSocket.instances[0].simulateMessage({
      type: 'ROOM_STATE',
      data: inviteRoomState(),
    })
    await flushPromises()

    expect(wrapper.find('.participant-card--me').text()).toContain('HOST')
    expect(wrapper.find('.room-code__copy').exists()).toBe(true)
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })

  it('sends only IN_PROGRESS then COMPLETED for HOST calibration and keeps the JOINED room open', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]
    socket.simulateOpen()
    socket.simulateMessage({ type: 'ROOM_STATE', data: inviteRoomState() })
    const readyPage = inviteReadyPageVm(wrapper)

    readyPage.notifyCalibrationStarted()
    readyPage.finishCalibration()
    socket.simulateMessage({ type: 'ROOM_STATE', data: inviteRoomState() })
    await flushPromises()

    const commands = socket.sent.slice(1).map((raw) => JSON.parse(raw))
    expect(commands).toEqual([
      { type: 'CALIBRATION_STATUS', calibrationStatus: 'IN_PROGRESS' },
      { type: 'CALIBRATION_STATUS', calibrationStatus: 'COMPLETED' },
    ])
    expect(commands).not.toContainEqual({
      type: 'READY_STATUS',
      isReady: true,
    })
    expect(readyPage.isCalibrated).toBe(true)
    expect(socket.readyState).toBe(MockWebSocket.OPEN)
    expect(socket.closeCalls).toBe(0)
    expect(wrapper.find('.participant-grid').exists()).toBe(true)
    expect(wrapper.find('.room-join-error').exists()).toBe(false)
  })

  it('does not auto-ready PLAYER after calibration and sends READY only from the ready action', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('PLAYER'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends&room=4827',
    ))
    const socket = MockWebSocket.instances[0]
    socket.simulateOpen()
    const playerState = inviteRoomState('invite-room', 'WAITING', [
      { ...inviteRoomState().participants[1], roomRole: 'HOST' },
      { ...inviteRoomState().participants[0], roomRole: 'PLAYER' },
    ])
    socket.simulateMessage({ type: 'ROOM_STATE', data: playerState })
    const readyPage = inviteReadyPageVm(wrapper)

    readyPage.notifyCalibrationStarted()
    readyPage.finishCalibration()
    expect(socket.sent.slice(1).map((raw) => JSON.parse(raw))).toEqual([
      { type: 'CALIBRATION_STATUS', calibrationStatus: 'IN_PROGRESS' },
      { type: 'CALIBRATION_STATUS', calibrationStatus: 'COMPLETED' },
    ])

    readyPage.markPlayerReady()
    expect(socket.sent.slice(1).map((raw) => JSON.parse(raw))).toEqual([
      { type: 'CALIBRATION_STATUS', calibrationStatus: 'IN_PROGRESS' },
      { type: 'CALIBRATION_STATUS', calibrationStatus: 'COMPLETED' },
      { type: 'READY_STATUS', isReady: true },
    ])
  })

  it('keeps JOINED, camera permission, and membership when calibration is cancelled', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]
    socket.simulateOpen()
    socket.simulateMessage({ type: 'ROOM_STATE', data: inviteRoomState() })
    const readyPage = inviteReadyPageVm(wrapper)
    readyPage.permissionStatus = 'granted'
    readyPage.isCalibrationOpen = true
    readyPage.notifyCalibrationStarted()

    readyPage.handleCalibrationBack()
    await flushPromises()

    expect(readyPage.isCalibrationOpen).toBe(false)
    expect(readyPage.permissionStatus).toBe('granted')
    expect(readyPage.isCalibrated).toBe(false)
    expect(socket.readyState).toBe(MockWebSocket.OPEN)
    expect(socket.closeCalls).toBe(0)
    expect(wrapper.find('.participant-grid').exists()).toBe(true)
    expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledTimes(1)

    readyPage.isCalibrationOpen = true
    expect(readyPage.isCalibrationOpen).toBe(true)
  })

  it('keeps JOINED command errors non-terminal and accepts later ROOM_STATE updates', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]
    socket.simulateOpen()
    socket.simulateMessage({ type: 'ROOM_STATE', data: inviteRoomState() })

    socket.simulateMessage({
      type: 'ERROR',
      data: {
        code: 'WAITING-014',
        message: '현재 상태에서는 요청한 상태로 변경할 수 없습니다.',
      },
    })
    await flushPromises()

    expect(useToast().message.value).toBe(
      '현재 상태에서는 요청한 상태로 변경할 수 없습니다.',
    )
    expect(socket.readyState).toBe(MockWebSocket.OPEN)
    expect(socket.closeCalls).toBe(0)
    expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.participant-grid').exists()).toBe(true)
    expect(wrapper.find('.room-join-error__retry').exists()).toBe(false)

    const updated = inviteRoomState()
    updated.participants[1].isReady = true
    socket.simulateMessage({ type: 'ROOM_STATE', data: updated })
    await flushPromises()
    expect(
      wrapper
        .find('.participant-card--opponent')
        .find('.complete-badge')
        .exists(),
    ).toBe(true)
  })

  it('moves a CONNECTING authentication error to FAILED and keeps preparation hidden', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]
    socket.simulateOpen()
    socket.simulateMessage({
      type: 'ERROR',
      data: {
        code: 'WAITING-012',
        message: 'WebSocket 인증 시간이 초과되었습니다.',
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('WebSocket 인증 시간이 초과되었습니다.')
    expect(wrapper.find('.participant-grid').exists()).toBe(false)
    expect(wrapper.find('.room-join-error__retry').exists()).toBe(true)
    expect(socket.closeCalls).toBe(1)
  })

  it('does not offer an existing-room reconnect for CONNECTING NOT_FOUND', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]
    socket.simulateOpen()
    socket.simulateMessage({
      type: 'ERROR',
      data: {
        code: 'WAITING-008',
        message: '대기방을 찾을 수 없습니다.',
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('종료되었거나 존재하지 않는 방이에요.')
    expect(wrapper.find('.participant-grid').exists()).toBe(false)
    expect(wrapper.find('.room-join-error__retry').exists()).toBe(false)
    expect(socket.closeCalls).toBe(1)
  })

  it('fails the current connection when its first snapshot has mismatched metadata', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]

    socket.simulateMessage({
      type: 'ROOM_STATE',
      data: { ...inviteRoomState(), roomType: 'RANDOM' },
    })
    await flushPromises()
    expect(wrapper.find('.participant-grid').exists()).toBe(false)
    expect(wrapper.text()).toContain('초대방 정보를 불러오지 못했어요.')
    expect(socket.closeCalls).toBe(1)
  })

  it('fails the current room when my participant is missing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]

    socket.simulateMessage({
      type: 'ROOM_STATE',
      data: inviteRoomState('invite-room', 'WAITING', []),
    })
    await flushPromises()
    expect(wrapper.text()).toContain('대기방 참가 정보를 확인할 수 없어요.')
    expect(wrapper.find('.participant-grid').exists()).toBe(false)
    expect(socket.closeCalls).toBe(1)
  })

  it('resynchronizes role on every valid snapshot instead of preserving a guessed role', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]
    socket.simulateMessage({ type: 'ROOM_STATE', data: inviteRoomState() })
    await flushPromises()
    expect(wrapper.find('.participant-card--me').text()).toContain('HOST')
    expect(wrapper.find('.room-code__copy').exists()).toBe(true)

    const changed = inviteRoomState('invite-room', 'WAITING', [
      { ...inviteRoomState().participants[0], roomRole: 'PLAYER' },
      { ...inviteRoomState().participants[1], roomRole: 'HOST' },
    ])
    socket.simulateMessage({ type: 'ROOM_STATE', data: changed })
    await flushPromises()

    expect(wrapper.find('.participant-card--me').text()).toContain('PLAYER')
    expect(wrapper.find('.participant-card--opponent').text()).toContain('HOST')
    expect(wrapper.find('.room-code__copy').exists()).toBe(false)
  })

  it('keeps current CLOSED as a terminal state when the server closes the socket and ignores late events', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const socket = MockWebSocket.instances[0]
    socket.simulateMessage({ type: 'ROOM_STATE', data: inviteRoomState() })
    socket.simulateMessage({
      type: 'ROOM_STATE',
      data: inviteRoomState('invite-room', 'CLOSED'),
    })
    socket.simulateUnexpectedClose()
    socket.simulateMessage({ type: 'ROOM_STATE', data: inviteRoomState() })
    socket.simulateMessage({
      type: 'GAME_START',
      data: {
        roomId: 'invite-room',
        gameName: 'EYEFIGHT',
        startedAt: '2026-08-05T00:00:03Z',
        openviduUrl: null,
        token: null,
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('초대방이 종료되었어요.')
    expect(wrapper.find('.participant-grid').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('재매칭')
  })

  it('retries an unexpected socket close against the existing room without another REST join', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    ;({ wrapper } = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends',
    ))
    const firstSocket = MockWebSocket.instances[0]
    firstSocket.simulateMessage({
      type: 'ROOM_STATE',
      data: inviteRoomState(),
    })
    firstSocket.simulateUnexpectedClose()
    await flushPromises()

    expect(wrapper.text()).toContain('대기방 연결이 예기치 않게 종료됐어요.')
    await wrapper.find('.room-join-error__retry').trigger('click')

    expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledTimes(1)
    expect(MockWebSocket.instances).toHaveLength(2)
    MockWebSocket.instances[1].simulateMessage({
      type: 'ROOM_STATE',
      data: inviteRoomState(),
    })
    await flushPromises()
    expect(wrapper.find('.participant-grid').exists()).toBe(true)
  })

  it.each([
    ['WAITING-003', '초대방 정보를 불러오지 못했어요.', true],
    ['WAITING-004', '존재하지 않거나 만료된 초대 코드예요.', false],
  ])(
    'renders %s with its retry policy and no preparation UI',
    async (code, message, retryable) => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => errorResponse(code, '서버 원문')),
      )
      ;({ wrapper } = await mountInviteReadyPage(
        '/games/hold/ready?mode=friends&room=4827',
      ))

      expect(wrapper.text()).toContain(message)
      expect(wrapper.find('.room-join-error__retry').exists()).toBe(retryable)
      expect(wrapper.find('.participant-grid').exists()).toBe(false)
    },
  )

  it('calls REST leave before intentionally closing a JOINED socket', async () => {
    let resolveLeave: ((response: Response) => void) | undefined
    const leaveResponse = new Promise<Response>((resolve) => {
      resolveLeave = resolve
    })
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(successResponse(inviteRestResponse('HOST')))
        .mockReturnValueOnce(leaveResponse),
    )
    const mounted = await mountInviteReadyPage('/games/hold/ready?mode=friends')
    wrapper = mounted.wrapper
    const socket = MockWebSocket.instances[0]
    socket.simulateMessage({ type: 'ROOM_STATE', data: inviteRoomState() })
    await flushPromises()

    ;(
      wrapper.find('.room-header__actions > button')
        .element as globalThis.HTMLButtonElement
    ).click()
    await Promise.resolve()

    const calls = vi.mocked(globalThis.fetch).mock.calls
    expect(String(calls[1]?.[0])).toContain('/waiting-rooms/invite-room/leave')
    expect(socket.closeCalls).toBe(0)

    resolveLeave?.(successResponse())
    await flushPromises()
    expect(socket.closeCalls).toBe(1)
  })

  it('invalidates an ENTERING request and best-effort leaves a stale successful response without opening a socket', async () => {
    let resolveCreate: ((response: Response) => void) | undefined
    const createResponse = new Promise<Response>((resolve) => {
      resolveCreate = resolve
    })
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockReturnValueOnce(createResponse)
        .mockResolvedValueOnce(successResponse()),
    )
    const router = createReadyRouter()
    await router.push('/games/hold/ready?mode=friends')
    await router.isReady()
    wrapper = mount(GameReadyPage, {
      global: { plugins: [router, createPinia()] },
    })

    await wrapper.find('.back--button').trigger('click')
    resolveCreate?.(successResponse(inviteRestResponse('HOST')))
    await flushPromises()

    const calls = vi.mocked(globalThis.fetch).mock.calls
    expect(calls).toHaveLength(2)
    expect(String(calls[1]?.[0])).toContain('/waiting-rooms/invite-room/leave')
    expect(MockWebSocket.instances).toHaveLength(0)
    expect(router.currentRoute.value.name).toBe('game-detail')
  })

  it('does not call leave again from FAILED or CLOSED', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => errorResponse('WAITING-004', 'not found')),
    )
    let mounted = await mountInviteReadyPage(
      '/games/hold/ready?mode=friends&room=4827',
    )
    wrapper = mounted.wrapper
    await wrapper.find('.room-join-error__back').trigger('click')
    await flushPromises()
    expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    wrapper = undefined
    MockWebSocket.instances = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => successResponse(inviteRestResponse('HOST'))),
    )
    mounted = await mountInviteReadyPage('/games/hold/ready?mode=friends')
    wrapper = mounted.wrapper
    MockWebSocket.instances[0].simulateMessage({
      type: 'ROOM_STATE',
      data: inviteRoomState('invite-room', 'CLOSED'),
    })
    await flushPromises()
    await wrapper.find('.room-join-error__back').trigger('click')
    await flushPromises()
    expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledTimes(1)
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
    await router.push('/games/hold/ready?mode=friends')
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
