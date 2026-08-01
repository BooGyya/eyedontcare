import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useMatchSocket } from './useMatchSocket'

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

  constructor(readonly url: string) {
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

describe('useMatchSocket', () => {
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
      const socket = useMatchSocket()
      socket.connect({ guestSessionId: 'guest-1' })
      const ws = MockWebSocket.instances[0]
      expect(ws.url).toContain('/ws/match')
      ws.simulateOpen()
      expect(socket.status.value).toBe('open')
      expect(JSON.parse(ws.sent[0])).toEqual({
        type: 'AUTH',
        guestSessionId: 'guest-1',
      })
    })
    scope.stop()
  })

  it('forwards MATCH_SUCCESS with the matched room id', () => {
    const scope = effectScope()
    scope.run(() => {
      const matched: { roomId: string; gameType: string }[] = []
      const socket = useMatchSocket({
        onMatchSuccess: (roomId, gameType) => matched.push({ roomId, gameType }),
      })
      socket.connect({ accessToken: 'jwt' })
      const ws = MockWebSocket.instances[0]
      ws.simulateOpen()
      ws.simulateMessage({
        type: 'MATCH_SUCCESS',
        roomId: 'room-9',
        gameType: 'EYEFIGHT',
      })

      expect(matched).toEqual([{ roomId: 'room-9', gameType: 'EYEFIGHT' }])
    })
    scope.stop()
  })

  it('surfaces MATCH_ERROR events', () => {
    const scope = effectScope()
    scope.run(() => {
      const errors: string[] = []
      const socket = useMatchSocket({
        onError: (code) => errors.push(code),
      })
      socket.connect({ accessToken: 'jwt' })
      const ws = MockWebSocket.instances[0]
      ws.simulateOpen()
      ws.simulateMessage({
        type: 'MATCH_ERROR',
        code: 'MATCHMAKING-002',
        message: '지원하지 않는 게임입니다.',
      })

      expect(socket.status.value).toBe('error')
      expect(socket.errorMessage.value).toBe('지원하지 않는 게임입니다.')
      expect(errors).toEqual(['MATCHMAKING-002'])
    })
    scope.stop()
  })
})
