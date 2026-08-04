export type RankingRecord = {
  rank: number
  value: number
  label: string
  nickname: string
  avatar: string
}

export type WeeklyRankingGame = {
  id: 'blink' | 'draw' | 'stare' | 'challenge' | 'air'
  title: string
  mode: string
  image: string
  tone: 'purple' | 'mint' | 'blue' | 'orange' | 'sky'
  unit: string
  records: RankingRecord[]
  myRank: number
}

export type QuickAction = {
  id: 'discord' | 'ranking' | 'group'
  title: string
  description: string
  image: string
  tone: 'blue' | 'yellow' | 'purple'
  destination?: '/ranking'
  externalUrl?: string
  notice?: string
}
