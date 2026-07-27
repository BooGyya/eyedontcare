export type GameStatus = 'available' | 'coming-soon'
export type GameId = 'air' | 'blink' | 'draw' | 'hold' | 'wave'

export type GameCatalogItem = {
  id: GameId
  title: string
  description: string
  image: string
  category: string
  status: GameStatus
}

export type RankingPlayer = {
  rank: number
  nickname: string
  score: string
  avatar: string
  level?: string
  record?: string
  trend?: 'up' | 'down' | 'same'
  isCurrentUser?: boolean
}

export type GameRanking = {
  gameId: GameId | 'all'
  gameName: string
  unit: string
  sortOrder: 'desc'
  players: RankingPlayer[]
  myRank: number
  myScore: string
  totalPlayers?: number
}

export type CommunityGroup = {
  id: string
  name: string
  description: string
  image: string
  members: number
  capacity: number
  status: 'open' | 'full'
}
