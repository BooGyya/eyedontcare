import type { GameName } from './waitingRoom'

export type GameOutcome = 'WIN' | 'LOSE' | 'DRAW' | 'COMPLETED'

/** 백엔드 `PlayMode` enum. */
export type GameResultPlayMode = 'SOLO' | 'INVITE' | 'RANDOM' | 'AI'

export type ParticipantType = 'USER' | 'GUEST' | 'BOT'

/** `GET /game-results/me` 목록의 한 건. 점수/참가자/시간은 상세에만 있다. */
export interface MyGameResult {
  resultId: number
  gameName: GameName
  playMode: GameResultPlayMode
  difficulty: number | null
  myOutcome: GameOutcome
  myRank: number
  playedAt: string
}

/** `GET /game-results/me` 응답(1-based page). */
export interface MyGameResultPage {
  content: MyGameResult[]
  page: number
  size: number
  totalElements: number
}

/** `GET /game-results/{id}` 상세 참가자. 점수/본인식별자는 담기지 않는다. */
export interface ParticipantResult {
  slotNo: number
  participantType: ParticipantType
  displayName: string
  outcome: GameOutcome
  rank: number
}

/** `POST /game-results` 요청의 참가자 한 명. */
export interface ParticipantResultRequest {
  participantKey: string
  participantType: ParticipantType
  slotNo: number
  displayName: string
  outcome: GameOutcome
  rank: number
}

/** `POST /game-results` 요청 본문. */
export interface SubmitGameResultRequest {
  playId: string
  gameId: number
  startedAt: string
  endedAt: string
  participants: ParticipantResultRequest[]
  gameResult: Record<string, unknown>
}

/** `POST /game-results` 응답 data. */
export interface SubmitGameResultResponse {
  resultId: number
}

/** `GET /game-results/{id}` 상세 응답. */
export interface GameResultDetailResponse {
  resultId: number
  gameName: GameName
  playMode: GameResultPlayMode
  difficulty: number | null
  startedAt: string
  endedAt: string
  participants: ParticipantResult[]
  /** 게임별 구조가 달라 그대로 저장/조회되는 JSON. */
  gameResult: Record<string, unknown>
}
