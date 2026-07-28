export type GameStatus = 'available' | 'coming-soon'

export type GameCatalogItem = {
  id: string
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
}

export type GameRanking = {
  gameId: string
  gameName: string
  unit: string
  players: RankingPlayer[]
  myRank: number
  myScore: string
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
