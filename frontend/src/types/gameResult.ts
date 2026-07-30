export type GameOutcome = 'WIN' | 'LOSE' | 'DRAW' | 'COMPLETED'

export type GameParticipant = {
  slotNo: number
  participantType: 'USER' | 'GUEST' | 'BOT'
  displayName: string
  outcome: GameOutcome
  rank: number
  score: number
}

export type GameResultDetail = {
  resultId: number
  gameName: string
  playMode: 'SINGLE' | 'MULTI'
  difficulty: number | null
  startedAt: string
  endedAt: string
  participants: GameParticipant[]
  gameResult: Record<string, { survivalTimeMs?: number }>
}
