import { onScopeDispose, readonly, ref } from 'vue'
import type { GameName, WaitingRoomIdentity } from '../types/waitingRoom'
import type { MatchServerEvent } from '../types/matchmaking'

export type MatchConnectionStatus =
  'idle' | 'connecting' | 'open' | 'closed' | 'error'

interface UseMatchSocketOptions {
  onMatchSuccess?: (roomId: string, gameType: GameName) => void
  onRequeued?: (gameType: GameName) => void
  onError?: (code: string, message: string) => void
  onOpen?: (generation: number) => void
  onUnexpectedClose?: (generation: number) => void
}

/**
 * 매칭 WebSocket 주소를 만든다. dev에서는 Vite가 `/ws`를 백엔드로 프록시(`ws:true`)한다.
 */
function resolveMatchWebSocketUrl(): string {
  const location = globalThis.location
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/match`
}

/**
 * 매칭 WebSocket 클라이언트 컴포저블.
 *
 * 소켓이 열리면 즉시 `AUTH` 프레임(회원 `accessToken` 또는 게스트 `guestSessionId`)을 보낸다.
 * 성사되면 서버가 `MATCH_SUCCESS`를 푸시하고, 인증 시점에 이미 성사돼 있으면 서버가 다시 보낸다.
 * 대기방 소켓과 달리 이벤트는 평평한 객체(`{type, roomId, ...}`)로 온다.
 */
export function useMatchSocket(options: UseMatchSocketOptions = {}) {
  const status = ref<MatchConnectionStatus>('idle')
  const errorMessage = ref<string | null>(null)
  const connectionGeneration = ref(0)
  const isConnected = ref(false)

  let socket: globalThis.WebSocket | null = null

  function connect(identity: WaitingRoomIdentity): boolean {
    if (
      socket &&
      (socket.readyState === globalThis.WebSocket.CONNECTING ||
        socket.readyState === globalThis.WebSocket.OPEN)
    ) {
      return false
    }

    connectionGeneration.value += 1
    const generation = connectionGeneration.value
    errorMessage.value = null
    isConnected.value = false
    status.value = 'connecting'

    const ws = new globalThis.WebSocket(resolveMatchWebSocketUrl())
    socket = ws

    ws.onopen = () => {
      if (!isCurrentSocket(ws, generation)) return
      status.value = 'open'
      isConnected.value = true
      ws.send(JSON.stringify({ type: 'AUTH', ...identity }))
      options.onOpen?.(generation)
    }
    ws.onmessage = (event) => {
      if (!isCurrentSocket(ws, generation)) return
      handleMessage(String(event.data))
    }
    ws.onerror = () => {
      if (!isCurrentSocket(ws, generation)) return
      status.value = 'error'
    }
    ws.onclose = () => {
      if (!isCurrentSocket(ws, generation)) return
      socket = null
      isConnected.value = false
      if (status.value !== 'error') status.value = 'closed'
      options.onUnexpectedClose?.(generation)
    }
    return true
  }

  function handleMessage(raw: string): void {
    let event: MatchServerEvent
    try {
      event = JSON.parse(raw) as MatchServerEvent
    } catch {
      return
    }
    switch (event.type) {
      case 'MATCH_SUCCESS':
        options.onMatchSuccess?.(event.roomId, event.gameType)
        break
      case 'MATCH_REQUEUED':
        options.onRequeued?.(event.gameType)
        break
      case 'MATCH_ERROR':
        errorMessage.value = event.message
        status.value = 'error'
        options.onError?.(event.code, event.message)
        break
    }
  }

  function close(): void {
    const active = socket
    connectionGeneration.value += 1
    socket = null
    isConnected.value = false
    status.value = 'closed'
    if (!active) return
    active.onopen = null
    active.onmessage = null
    active.onerror = null
    active.onclose = null
    if (
      active.readyState === globalThis.WebSocket.OPEN ||
      active.readyState === globalThis.WebSocket.CONNECTING
    ) {
      active.close()
    }
  }

  function isCurrentSocket(
    candidate: globalThis.WebSocket,
    generation: number,
  ): boolean {
    return socket === candidate && connectionGeneration.value === generation
  }

  onScopeDispose(close)

  return {
    status: readonly(status),
    errorMessage: readonly(errorMessage),
    connectionGeneration: readonly(connectionGeneration),
    isConnected: readonly(isConnected),
    connect,
    close,
  }
}
