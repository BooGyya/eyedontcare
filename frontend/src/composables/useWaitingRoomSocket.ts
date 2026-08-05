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
  'idle' | 'connecting' | 'open' | 'closed' | 'error'

export interface WaitingRoomSocketContext {
  roomId: string
  generation: number
}

interface UseWaitingRoomSocketOptions {
  onRoomState?: (
    state: WaitingRoomStateData,
    context: WaitingRoomSocketContext,
  ) => void
  onGameStart?: (
    data: WaitingRoomGameStartData,
    context: WaitingRoomSocketContext,
  ) => void
  onError?: (
    code: string,
    message: string,
    context: WaitingRoomSocketContext,
  ) => void
  onUnexpectedClose?: (context: WaitingRoomSocketContext) => void
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
  const connectedRoomId = ref<string | null>(null)
  const connectionGeneration = ref(0)

  let socket: globalThis.WebSocket | null = null
  let expectedServerCloseSocket: globalThis.WebSocket | null = null

  function connect(roomId: string, identity: WaitingRoomIdentity): boolean {
    if (
      socket &&
      connectedRoomId.value === roomId &&
      (socket.readyState === globalThis.WebSocket.CONNECTING ||
        socket.readyState === globalThis.WebSocket.OPEN)
    ) {
      return false
    }

    close()
    connectionGeneration.value += 1
    const generation = connectionGeneration.value
    const context = { roomId, generation }
    errorMessage.value = null
    roomState.value = null
    connectedRoomId.value = roomId
    status.value = 'connecting'

    const ws = new globalThis.WebSocket(resolveWebSocketUrl(roomId))
    socket = ws

    ws.onopen = () => {
      if (!isCurrentSocket(ws, context)) return
      ws.send(JSON.stringify({ type: 'AUTH', ...identity }))
    }
    ws.onmessage = (event) => {
      if (!isCurrentSocket(ws, context)) return
      handleMessage(String(event.data), context)
    }
    ws.onerror = () => {
      if (expectedServerCloseSocket === ws) return
      if (!isCurrentSocket(ws, context)) return
      status.value = 'error'
    }
    ws.onclose = () => {
      if (expectedServerCloseSocket === ws) {
        expectedServerCloseSocket = null
        if (socket === ws) {
          socket = null
          status.value = 'closed'
        }
        return
      }
      if (!isCurrentSocket(ws, context)) return
      socket = null
      connectedRoomId.value = null
      if (status.value !== 'error') status.value = 'closed'
      options.onUnexpectedClose?.(context)
    }
    return true
  }

  function handleMessage(raw: string, context: WaitingRoomSocketContext): void {
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
        options.onRoomState?.(event.data, context)
        break
      case 'GAME_START':
        options.onGameStart?.(event.data, context)
        break
      case 'ERROR':
        errorMessage.value = event.data.message
        status.value = 'error'
        options.onError?.(event.data.code, event.data.message, context)
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

  /**
   * 현재 방 이벤트를 stale 처리하되 실제 연결은 서버가 닫을 때까지 유지한다.
   * RANDOM CLOSED 직후 client close가 자동 재매칭 entry 삭제로 해석되는 경쟁을 피하기 위해 사용한다.
   */
  function invalidateCurrentConnectionEvents(): void {
    connectionGeneration.value += 1
    expectedServerCloseSocket = socket
    connectedRoomId.value = null
    roomState.value = null
    errorMessage.value = null
  }

  function close(): void {
    const active = socket
    connectionGeneration.value += 1
    socket = null
    expectedServerCloseSocket = null
    connectedRoomId.value = null
    roomState.value = null
    errorMessage.value = null
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
    context: WaitingRoomSocketContext,
  ): boolean {
    return (
      socket === candidate &&
      connectedRoomId.value === context.roomId &&
      connectionGeneration.value === context.generation
    )
  }

  onScopeDispose(close)

  return {
    status: readonly(status),
    roomState: readonly(roomState),
    errorMessage: readonly(errorMessage),
    connectedRoomId: readonly(connectedRoomId),
    connectionGeneration: readonly(connectionGeneration),
    connect,
    invalidateCurrentConnectionEvents,
    close,
    sendCalibrationStatus,
    sendReady,
    sendStartGame,
  }
}
