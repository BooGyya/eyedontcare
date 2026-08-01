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
})
