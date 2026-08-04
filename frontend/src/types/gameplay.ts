import type { GameDetailId, GamePlayMode } from './game-detail'
import type { DrawRoundResult } from '../lib/games/draw-core'

export type GameSessionMode = GamePlayMode['id']

export type GameSession = {
  gameId: GameDetailId
  mode: GameSessionMode
  roomCode?: string
  round: number
  totalRounds: number
  score: number
  opponentScore?: number
  timeLabel: string
}

export type GameResultStat = {
  label: string
  /** 내 값(항상 사용). 나-상대 구분이 없는 공용 정보면 이 값만 있으면 된다. */
  value: string
  /** 상대 값. 생략하면 결과 화면이 `value`를 좌우 동일하게 보여준다(눈싸움/리듬처럼
   * 나-상대 구분이 의미 없는 공용 정보용) — 득점/실점처럼 실제로 다른 값이면 반드시 채워야 한다. */
  opponentValue?: string
}

export type GameResult = {
  gameId: GameDetailId
  headline: string
  summary: string
  scoreLabel: string
  score: string
  opponentScore?: string
  opponentNickname?: string
  stats: GameResultStat[]
  drawRounds?: DrawRoundResult[]
}
