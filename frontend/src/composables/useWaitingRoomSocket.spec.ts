import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useWaitingRoomSocket } from './useWaitingRoomSocket'
import type {
  WaitingRoomGameStartData,
  WaitingRoomStateData,
} from '../types/waitingRoom'

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

  simulateError(): void {
    this.onerror?.()
  }

  simulateUnexpectedClose(): void {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.()
  }
}

const roomState: WaitingRoomStateData = {
  roomId: 'room-1',
  roomType: 'INVITE',
  gameName: 'EYEFIGHT',
  roomCode: '1234',
  roomStatus: 'WAITING',
  countdownEndsAt: null,
  participants: [],
  createdAt: '2026-07-31T00:00:00Z',
}

describe('useWaitingRoomSocket', () => {
  beforeEach(() => {
    MockWebSocket.instances = []
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends the AUTH frame once the socket opens', () => {
    const scope = effectScope()
    scope.run(() => {
      const socket = useWaitingRoomSocket()
      socket.connect('room-1', { guestSessionId: 'guest-1' })
      const ws = MockWebSocket.instances[0]
      expect(ws.url).toContain('/ws/waiting-rooms/room-1')
      ws.simulateOpen()
      expect(JSON.parse(ws.sent[0])).toEqual({
        type: 'AUTH',
        guestSessionId: 'guest-1',
      })
    })
    scope.stop()
  })

  it('exposes ROOM_STATE and marks the connection open', () => {
    const scope = effectScope()
    scope.run(() => {
      const received: WaitingRoomStateData[] = []
      const socket = useWaitingRoomSocket({
        onRoomState: (state) => received.push(state),
      })
      socket.connect('room-1', { accessToken: 'jwt' })
      const ws = MockWebSocket.instances[0]
      ws.simulateOpen()
      ws.simulateMessage({ type: 'ROOM_STATE', data: roomState })

      expect(socket.status.value).toBe('open')
      expect(socket.roomState.value?.roomCode).toBe('1234')
      expect(socket.connectedRoomId.value).toBe('room-1')
      expect(received).toHaveLength(1)
    })
    scope.stop()
  })

  it('forwards GAME_START media credentials to the callback', () => {
    const scope = effectScope()
    scope.run(() => {
      const starts: WaitingRoomGameStartData[] = []
      const socket = useWaitingRoomSocket({
        onGameStart: (data) => starts.push(data),
      })
      socket.connect('room-1', { accessToken: 'jwt' })
      const ws = MockWebSocket.instances[0]
      ws.simulateOpen()
      ws.simulateMessage({
        type: 'GAME_START',
        data: {
          roomId: 'room-1',
          gameName: 'EYEFIGHT',
          startedAt: '2026-07-31T00:00:01Z',
          openviduUrl: 'wss://media.example:7443',
          token: 'livekit-token',
        },
      })

      expect(starts[0]?.token).toBe('livekit-token')
      expect(starts[0]?.openviduUrl).toBe('wss://media.example:7443')
    })
    scope.stop()
  })

  it('surfaces ERROR events', () => {
    const scope = effectScope()
    scope.run(() => {
      const errors: string[] = []
      const socket = useWaitingRoomSocket({
        onError: (code) => errors.push(code),
      })
      socket.connect('room-1', { accessToken: 'jwt' })
      const ws = MockWebSocket.instances[0]
      ws.simulateOpen()
      ws.simulateMessage({
        type: 'ERROR',
        data: { code: 'WR_0001', message: '참가자를 찾을 수 없습니다.' },
      })

      expect(socket.status.value).toBe('error')
      expect(socket.errorMessage.value).toBe('참가자를 찾을 수 없습니다.')
      expect(errors).toEqual(['WR_0001'])
    })
    scope.stop()
  })

  it('sends command frames only while open', () => {
    const scope = effectScope()
    scope.run(() => {
      const socket = useWaitingRoomSocket()
      socket.connect('room-1', { accessToken: 'jwt' })
      const ws = MockWebSocket.instances[0]

      socket.sendReady(true)
      expect(ws.sent).toHaveLength(0)

      ws.simulateOpen()
      socket.sendCalibrationStatus('COMPLETED')
      socket.sendReady(true)
      socket.sendStartGame()

      const frames = ws.sent.slice(1).map((raw) => JSON.parse(raw))
      expect(frames).toEqual([
        { type: 'CALIBRATION_STATUS', calibrationStatus: 'COMPLETED' },
        { type: 'READY_STATUS', isReady: true },
        { type: 'START_GAME' },
      ])
    })
    scope.stop()
  })

  it('does not create another socket for the same active room', () => {
    const scope = effectScope()
    scope.run(() => {
      const socket = useWaitingRoomSocket()
      expect(socket.connect('room-1', { accessToken: 'jwt' })).toBe(true)
      expect(socket.connect('room-1', { accessToken: 'jwt' })).toBe(false)
      MockWebSocket.instances[0].simulateOpen()
      expect(socket.connect('room-1', { accessToken: 'jwt' })).toBe(false)
      expect(MockWebSocket.instances).toHaveLength(1)
    })
    scope.stop()
  })

  it('invalidates old events without client close and accepts its server close as expected', () => {
    const scope = effectScope()
    scope.run(() => {
      const states: string[] = []
      const errors = vi.fn()
      const starts = vi.fn()
      const unexpected = vi.fn()
      const socket = useWaitingRoomSocket({
        onRoomState: (state) => states.push(state.roomId),
        onGameStart: starts,
        onError: errors,
        onUnexpectedClose: unexpected,
      })
      socket.connect('room-1', { accessToken: 'jwt' })
      const old = MockWebSocket.instances[0]
      old.simulateOpen()
      old.simulateMessage({ type: 'ROOM_STATE', data: roomState })
      const generation = socket.connectionGeneration.value

      socket.invalidateCurrentConnectionEvents()

      expect(old.closeCalls).toBe(0)
      expect(old.readyState).toBe(MockWebSocket.OPEN)
      expect(socket.connectionGeneration.value).toBe(generation + 1)
      expect(socket.connectedRoomId.value).toBeNull()
      expect(socket.roomState.value).toBeNull()

      old.simulateMessage({
        type: 'ROOM_STATE',
        data: { ...roomState, roomStatus: 'CLOSED' },
      })
      old.simulateMessage({
        type: 'GAME_START',
        data: {
          roomId: 'room-1',
          gameName: 'EYEFIGHT',
          startedAt: '2026-07-31T00:00:01Z',
          openviduUrl: null,
          token: null,
        },
      })
      old.simulateMessage({
        type: 'ERROR',
        data: { code: 'WR_0001', message: 'stale error' },
      })
      old.simulateError()

      expect(states).toEqual(['room-1'])
      expect(starts).not.toHaveBeenCalled()
      expect(errors).not.toHaveBeenCalled()
      expect(socket.status.value).toBe('open')

      old.simulateUnexpectedClose()

      expect(unexpected).not.toHaveBeenCalled()
      expect(socket.status.value).toBe('closed')
    })
    scope.stop()
  })

  it('clears old state and ignores callbacks from the previous room', () => {
    const scope = effectScope()
    scope.run(() => {
      const received: string[] = []
      const unexpected = vi.fn()
      const socket = useWaitingRoomSocket({
        onRoomState: (state) => received.push(state.roomId),
        onUnexpectedClose: unexpected,
      })
      socket.connect('room-1', { accessToken: 'jwt' })
      const old = MockWebSocket.instances[0]
      old.simulateOpen()
      old.simulateMessage({ type: 'ROOM_STATE', data: roomState })
      const oldMessage = old.onmessage
      const oldClose = old.onclose
      const oldError = old.onerror

      socket.connect('room-2', { accessToken: 'jwt' })
      expect(old.closeCalls).toBe(1)
      expect(socket.roomState.value).toBeNull()
      expect(socket.errorMessage.value).toBeNull()
      expect(socket.connectedRoomId.value).toBe('room-2')

      oldMessage?.({
        data: JSON.stringify({
          type: 'ROOM_STATE',
          data: { ...roomState, roomId: 'room-1', roomStatus: 'CLOSED' },
        }),
      })
      oldError?.()
      oldClose?.()

      expect(received).toEqual(['room-1'])
      expect(socket.status.value).toBe('connecting')
      expect(socket.connectedRoomId.value).toBe('room-2')
      expect(unexpected).not.toHaveBeenCalled()
    })
    scope.stop()
  })

  it('reports only an unexpected close from the current room', () => {
    const scope = effectScope()
    scope.run(() => {
      const unexpected = vi.fn()
      const socket = useWaitingRoomSocket({
        onUnexpectedClose: unexpected,
      })
      socket.connect('room-1', { accessToken: 'jwt' })
      const ws = MockWebSocket.instances[0]
      ws.simulateOpen()
      ws.simulateUnexpectedClose()

      expect(unexpected).toHaveBeenCalledWith({
        roomId: 'room-1',
        generation: expect.any(Number),
      })

      socket.connect('room-2', { accessToken: 'jwt' })
      socket.close()
      expect(unexpected).toHaveBeenCalledTimes(1)
    })
    scope.stop()
  })
})
