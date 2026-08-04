/**
 * 게임 세션(실시간 동기화) WebSocket 계약 타입.
 *
 * 백엔드 `/ws/game-sessions/{roomId}`와 1:1로 대응한다. 대기방 WebSocket이 `GAME_START`를
 * 보낸 뒤 연결을 끊기 때문에, 실제 게임 플레이 구간(눈 감김/점수 등 상대방 상태 실시간 반영)은
 * 이 채널을 따로 쓴다. 인증 프레임/신원 타입은 대기방과 동일해서 그대로 재사용한다.
 */
import type { GameName, WaitingRoomParticipant } from './waitingRoom'

export type { WaitingRoomIdentity as GameSessionIdentity } from './waitingRoom'

/** WS `SESSION_STATE` 이벤트 data — 접속 직후 한 번 온다. */
export interface GameSessionStateData {
  roomId: string
  gameName: GameName
  participants: WaitingRoomParticipant[]
}

/**
 * WS `PLAYER_EVENT` 이벤트 data — 상대방이 보낸 게임 이벤트.
 * `eventType`/`payload`는 게임마다 자유 형식이다(서버는 내용을 해석하지 않고 그대로 중계한다).
 * 예) 눈싸움: `{ eventType: 'EYE_STATE', payload: { combinedState: 'BOTH_OPEN' } }`
 *     눈 깜빡이기: `{ eventType: 'BLINK_COUNT', payload: { count: 12 } }`
 */
export interface GameSessionPlayerEventData {
  participantKey: string
  eventType: string
  payload: Record<string, unknown> | null
  occurredAt: string
}

/** WS `PARTICIPANT_LEFT` 이벤트 data — 상대방이 연결을 끊었을 때. */
export interface GameSessionParticipantLeftData {
  participantKey: string
}

/** WS `ERROR` 이벤트 data. */
export interface GameSessionErrorData {
  code: string
  message: string
}

/** 서버 → 클라이언트 이벤트(`{type, data}` 엔벨로프). */
export type GameSessionServerEvent =
  | { type: 'SESSION_STATE'; data: GameSessionStateData }
  | { type: 'PLAYER_EVENT'; data: GameSessionPlayerEventData }
  | { type: 'PARTICIPANT_LEFT'; data: GameSessionParticipantLeftData }
  | { type: 'ERROR'; data: GameSessionErrorData }

/** 클라이언트 → 서버 첫 프레임(소켓 오픈 후 인증 타임아웃 내 전송). */
export type GameSessionAuthFrame =
  | { type: 'AUTH'; accessToken: string }
  | { type: 'AUTH'; guestSessionId: string }

/** 클라이언트 → 서버 게임 이벤트 프레임 — 내가 보낸 이벤트를 상대에게 그대로 릴레이해 달라는 요청. */
export interface GameSessionPlayerEventFrame {
  type: 'PLAYER_EVENT'
  eventType: string
  payload?: Record<string, unknown>
  /** 클라이언트 쪽 발생 시각(epoch ms). 생략하면 서버 수신 시각을 쓴다. */
  occurredAt?: number
}
