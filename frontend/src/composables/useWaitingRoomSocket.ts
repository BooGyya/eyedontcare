import { onScopeDispose, readonly, ref } from 'vue'
import type {
  CalibrationStatus,
  WaitingRoomCommandFrame,
  WaitingRoomGameStartData,
  WaitingRoomIdentity,
  WaitingRoomServerEvent,
  WaitingRoomStateData,
} from '../types/waitingRoom'

export type WaitingRoomConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'open'
  | 'closed'
  | 'error'

interface UseWaitingRoomSocketOptions {
  onRoomState?: (state: WaitingRoomStateData) => void
  onGameStart?: (data: WaitingRoomGameStartData) => void
  onError?: (code: string, message: string) => void
}

/**
 * 현재 페이지 origin 기준으로 대기방 WebSocket 주소를 만든다. dev에서는 Vite가 `/ws`를
 * 백엔드로 프록시하므로 상대 경로만으로 충분하다.
 */
function resolveWebSocketUrl(roomId: string): string {
  const location = globalThis.location
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/waiting-rooms/${roomId}`
}

/**
 * 대기방 WebSocket 클라이언트 컴포저블.
 *
 * 소켓이 열리면 즉시 `AUTH` 프레임을 보내고(서버는 5초 안에 인증을 기대한다), 서버가 첫 `ROOM_STATE`를
 * 보내면 인증 성공으로 간주한다. 이후 방 상태를 `roomState`로 노출하고, 준비/캘리브레이션/시작 커맨드를
 * 전송한다. `GAME_START`를 받으면 미디어 접속 정보를 콜백으로 넘긴다.
 */
export function useWaitingRoomSocket(
  options: UseWaitingRoomSocketOptions = {},
) {
  const status = ref<WaitingRoomConnectionStatus>('idle')
  const roomState = ref<WaitingRoomStateData | null>(null)
  const errorMessage = ref<string | null>(null)

  let socket: globalThis.WebSocket | null = null

  function connect(roomId: string, identity: WaitingRoomIdentity): void {
    close()
    errorMessage.value = null
    roomState.value = null
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
    let event: WaitingRoomServerEvent
    try {
      event = JSON.parse(raw) as WaitingRoomServerEvent
    } catch {
      return
    }
    switch (event.type) {
      case 'ROOM_STATE':
        status.value = 'open'
        roomState.value = event.data
        options.onRoomState?.(event.data)
        break
      case 'GAME_START':
        options.onGameStart?.(event.data)
        break
      case 'ERROR':
        errorMessage.value = event.data.message
        status.value = 'error'
        options.onError?.(event.data.code, event.data.message)
        break
    }
  }

  function send(frame: WaitingRoomCommandFrame): void {
    if (socket && socket.readyState === globalThis.WebSocket.OPEN) {
      socket.send(JSON.stringify(frame))
    }
  }

  function sendCalibrationStatus(calibrationStatus: CalibrationStatus): void {
    send({ type: 'CALIBRATION_STATUS', calibrationStatus })
  }

  function sendReady(isReady: boolean): void {
    send({ type: 'READY_STATUS', isReady })
  }

  function sendStartGame(): void {
    send({ type: 'START_GAME' })
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
    roomState: readonly(roomState),
    errorMessage: readonly(errorMessage),
    connect,
    close,
    sendCalibrationStatus,
    sendReady,
    sendStartGame,
  }
}
