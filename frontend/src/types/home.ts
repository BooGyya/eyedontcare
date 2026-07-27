export type RankingRecord = {
  rank: number
  value: number
  label: string
}

export type WeeklyRankingGame = {
  id: 'blink' | 'draw' | 'stare' | 'challenge'
  title: string
  mode: string
  image: string
  tone: 'purple' | 'mint' | 'blue' | 'orange'
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
  notice?: string
}
