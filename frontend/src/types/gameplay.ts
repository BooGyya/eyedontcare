import type { GameDetailId, GamePlayMode } from './game-detail'

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

export type GameResult = {
  gameId: GameDetailId
  headline: string
  summary: string
  scoreLabel: string
  score: string
  opponentScore?: string
  stats: Array<{ label: string; value: string }>
}
