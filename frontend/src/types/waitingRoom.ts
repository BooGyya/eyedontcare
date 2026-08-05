/**
 * 대기방(waiting room) REST/WebSocket 계약 타입.
 *
 * 백엔드 `/api/v1/waiting-rooms` 및 `/ws/waiting-rooms/{roomId}`와 1:1로 대응한다.
 * 필드 이름과 enum 문자열은 서버 직렬화 결과와 정확히 일치해야 한다.
 */
import type { GameDetailId } from './game-detail'

export type GameName = 'EYEFIGHT' | 'BLINK' | 'DRAWING' | 'RHYTHM' | 'HOCKEY'
export type RoomType = 'INVITE' | 'RANDOM'
export type RoomRole = 'HOST' | 'PLAYER'
export type RoomStatus = 'WAITING' | 'COUNTDOWN' | 'IN_GAME' | 'CLOSED'
export type CalibrationStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'

/**
 * 프론트 게임 id → 백엔드 GameName enum 이름.
 * `hold → EYEFIGHT`만 mock 데이터로 직접 확인되었고 나머지는 테마 기준 추론이다.
 */
export const GAME_NAME_BY_ID: Record<GameDetailId, GameName> = {
  air: 'HOCKEY',
  hold: 'EYEFIGHT',
  draw: 'DRAWING',
  rhythm: 'RHYTHM',
  blink: 'BLINK',
}

/** 백엔드 `GameName` enum → 사용자에게 보여줄 한글 게임명. */
export const GAME_DISPLAY_NAME: Record<GameName, string> = {
  EYEFIGHT: '눈싸움',
  BLINK: '눈 깜빡이기',
  DRAWING: '눈으로 그리기',
  RHYTHM: '리듬 게임',
  HOCKEY: '에어하키',
}

export interface WaitingRoomParticipant {
  participantKey: string
  displayName: string
  roomRole: RoomRole
  slotNo: number
  isReady: boolean
  calibrationStatus: CalibrationStatus
  joinedAt: string
}

/** `POST /api/v1/waiting-rooms` (invite 생성) 응답 data. */
export interface WaitingRoomCreateResponse {
  roomId: string
  roomType: RoomType
  gameName: GameName
  roomCode: string
  roomStatus: RoomStatus
  participant: WaitingRoomParticipant
  guestSessionId?: string
  guestNickname?: string
  openviduUrl?: string | null
  token?: string | null
}

/** `POST /api/v1/waiting-rooms/join` (invite 참가) 응답 data. */
export interface WaitingRoomJoinResponse {
  roomId: string
  roomType: RoomType
  gameName: GameName
  roomCode: string
  roomStatus: RoomStatus
  participants: WaitingRoomParticipant[]
  createdAt: string
  guestSessionId?: string
  guestNickname?: string
  openviduUrl?: string | null
  token?: string | null
}

/** WS `ROOM_STATE` 이벤트 data. */
export interface WaitingRoomStateData {
  roomId: string
  roomType: RoomType
  gameName: GameName
  roomCode: string
  roomStatus: RoomStatus
  countdownEndsAt: string | null
  participants: WaitingRoomParticipant[]
  createdAt: string
}

/** WS `GAME_START` 이벤트 data. `token`은 수신자별로 다르며 미디어 미연동 시 null. */
export interface WaitingRoomGameStartData {
  roomId: string
  gameName: GameName
  startedAt: string
  openviduUrl: string | null
  token: string | null
}

/** WS `ERROR` 이벤트 data. */
export interface WaitingRoomErrorData {
  code: string
  message: string
}

/** 서버 → 클라이언트 이벤트(`{type, data}` 엔벨로프). */
export type WaitingRoomServerEvent =
  | { type: 'ROOM_STATE'; data: WaitingRoomStateData }
  | { type: 'GAME_START'; data: WaitingRoomGameStartData }
  | { type: 'ERROR'; data: WaitingRoomErrorData }

/** 클라이언트 → 서버 첫 프레임(소켓 오픈 후 5초 내 전송). */
export type WaitingRoomAuthFrame =
  | { type: 'AUTH'; accessToken: string }
  | { type: 'AUTH'; guestSessionId: string }

/** 클라이언트 → 서버 커맨드 프레임. */
export type WaitingRoomCommandFrame =
  | { type: 'CALIBRATION_STATUS'; calibrationStatus: CalibrationStatus }
  | { type: 'READY_STATUS'; isReady: boolean }
  | { type: 'START_GAME' }

/** 대기방 인증에 사용할 신원(둘 중 하나). */
export type WaitingRoomIdentity =
  | { accessToken: string }
  | { guestSessionId: string }
