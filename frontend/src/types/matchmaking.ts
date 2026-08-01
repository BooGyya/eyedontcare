/**
 * 랜덤 매칭(matchmaking) REST/WebSocket 계약 타입.
 *
 * 백엔드 `/api/v1/match` 및 `/ws/match`와 1:1로 대응한다.
 * 대기방 WebSocket과 달리 매칭 이벤트는 `{type, data}` 엔벨로프가 아니라 **평평한** 객체다
 * (예: `{"type":"MATCH_SUCCESS","roomId":"...","gameType":"EYEFIGHT"}`).
 */
import type { GameName } from './waitingRoom'

/** 매칭 엔트리 상태. `CANCELLED`는 취소 응답에만 쓰인다(저장되지 않음). */
export type MatchStatus =
  | 'SEARCHING'
  | 'MATCHING'
  | 'ENTERING_ROOM'
  | 'IN_WAITING_ROOM'
  | 'CANCELLED'

/**
 * `POST /api/v1/match/join`, `DELETE /api/v1/match/cancel` 응답 data.
 *
 * `waitingRoomId`는 이미 매칭이 성사돼 대기방으로 이동 중일 때만 채워진다. WebSocket
 * `MATCH_SUCCESS`를 놓친 경우의 fallback으로, 값이 있으면 곧바로 대기방에 접속할 수 있다.
 */
export interface MatchStatusResponse {
  participantKey: string
  gameType: GameName
  matchStatus: MatchStatus
  waitingRoomId: string | null
  queuedAt: string
  /** 게스트에게만 내려온다. 이후 요청·WebSocket에서 같은 세션을 재사용하도록 저장한다. */
  guestSessionId?: string
  guestNickname?: string
}

/** 서버 → 클라이언트 매칭 이벤트(평평한 객체, 엔벨로프 없음). */
export type MatchServerEvent =
  | { type: 'MATCH_SUCCESS'; roomId: string; gameType: GameName }
  | { type: 'MATCH_REQUEUED'; gameType: GameName }
  | { type: 'MATCH_ERROR'; code: string; message: string }
