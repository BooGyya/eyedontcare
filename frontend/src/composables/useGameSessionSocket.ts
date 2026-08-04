import { onScopeDispose, readonly, ref } from 'vue'
import type {
  GameSessionErrorData,
  GameSessionIdentity,
  GameSessionParticipantLeftData,
  GameSessionPlayerEventData,
  GameSessionServerEvent,
  GameSessionStateData,
} from '../types/gameSession'

export type GameSessionConnectionStatus =
  'idle' | 'connecting' | 'open' | 'closed' | 'error'

interface UseGameSessionSocketOptions {
  onSessionState?: (state: GameSessionStateData) => void
  onPlayerEvent?: (event: GameSessionPlayerEventData) => void
  onParticipantLeft?: (data: GameSessionParticipantLeftData) => void
  onError?: (code: string, message: string) => void
}

/**
 * 현재 페이지 origin 기준으로 게임 세션 WebSocket 주소를 만든다. dev에서는 Vite가 `/ws`를
 * 백엔드로 프록시하므로 상대 경로만으로 충분하다(useWaitingRoomSocket과 동일한 방식).
 */
function resolveWebSocketUrl(roomId: string): string {
  const location = globalThis.location
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/game-sessions/${roomId}`
}

/**
 * 게임 플레이 중 상대방과 실시간으로 상태를 주고받는 WebSocket 클라이언트 컴포저블.
 *
 * 대기방 WebSocket(`useWaitingRoomSocket`)은 GAME_START 직후 연결이 끊기므로, 게임 화면
 * (`GamePlayPage.vue`)에서 이 컴포저블로 별도 접속한다. 소켓이 열리면 AUTH 프레임을 보내고,
 * 서버의 SESSION_STATE 수신을 인증 성공으로 간주한다. 이후 `sendPlayerEvent`로 내 이벤트를
 * 보내고, 상대방 이벤트는 `onPlayerEvent` 콜백으로 받는다.
 *
 * ⚠️ 이 채널은 서버가 이벤트를 검증하지 않고 그대로 중계만 한다 — 최종 점수/승패는 여전히
 * 각자의 게임 로직이 판정하고 `useGameResultSubmission`으로 저장한다. 여기서 받은 상대방
 * 이벤트는 "화면에 보여주기용"으로만 쓴다.
 */
export function useGameSessionSocket(
  options: UseGameSessionSocketOptions = {},
) {
  const status = ref<GameSessionConnectionStatus>('idle')
  const sessionState = ref<GameSessionStateData | null>(null)
  const errorMessage = ref<string | null>(null)

  let socket: globalThis.WebSocket | null = null

  function connect(roomId: string, identity: GameSessionIdentity): void {
    close()
    errorMessage.value = null
    sessionState.value = null
    status.value = 'connecting'

    const ws = new globalThis.WebSocket(resolveWebSocketUrl(roomId))
    socket = ws

    ws.onopen = () => {
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
    let event: GameSessionServerEvent
    try {
      event = JSON.parse(raw) as GameSessionServerEvent
    } catch {
      return
    }
    switch (event.type) {
      case 'SESSION_STATE':
        status.value = 'open'
        sessionState.value = event.data
        options.onSessionState?.(event.data)
        break
      case 'PLAYER_EVENT':
        options.onPlayerEvent?.(event.data)
        break
      case 'PARTICIPANT_LEFT':
        options.onParticipantLeft?.(event.data)
        break
      case 'ERROR':
        errorMessage.value = (event.data as GameSessionErrorData).message
        status.value = 'error'
        options.onError?.(event.data.code, event.data.message)
        break
    }
  }

  /** 내 게임 이벤트를 상대에게 중계해 달라고 서버에 보낸다(예: 눈 감김, 점수 변화). */
  function sendPlayerEvent(
    eventType: string,
    payload?: Record<string, unknown>,
  ): void {
    if (!socket || socket.readyState !== globalThis.WebSocket.OPEN) return
    socket.send(
      JSON.stringify({
        type: 'PLAYER_EVENT',
        eventType,
        payload,
        occurredAt: Date.now(),
      }),
    )
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
    sessionState: readonly(sessionState),
    errorMessage: readonly(errorMessage),
    connect,
    close,
    sendPlayerEvent,
  }
}
