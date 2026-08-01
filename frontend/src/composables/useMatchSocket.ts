import { onScopeDispose, readonly, ref } from 'vue'
import type { GameName, WaitingRoomIdentity } from '../types/waitingRoom'
import type { MatchServerEvent } from '../types/matchmaking'

export type MatchConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'open'
  | 'closed'
  | 'error'

interface UseMatchSocketOptions {
  onMatchSuccess?: (roomId: string, gameType: GameName) => void
  onRequeued?: (gameType: GameName) => void
  onError?: (code: string, message: string) => void
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

  let socket: globalThis.WebSocket | null = null

  function connect(identity: WaitingRoomIdentity): void {
    close()
    errorMessage.value = null
    status.value = 'connecting'

    const ws = new globalThis.WebSocket(resolveMatchWebSocketUrl())
    socket = ws

    ws.onopen = () => {
      status.value = 'open'
      ws.send(JSON.stringify({ type: 'AUTH', ...identity }))
    }
    ws.onmessage = (event) => handleMessage(String(event.data))
    ws.onerror = () => {
      status.value = 'error'
    }
    ws.onclose = () => {
      if (socket === ws) socket = null
      if (status.value !== 'error') status.value = 'closed'
    }
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
    socket = null
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

  onScopeDispose(close)

  return {
    status: readonly(status),
    errorMessage: readonly(errorMessage),
    connect,
    close,
  }
}
