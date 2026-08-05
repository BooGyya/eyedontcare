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

/** 카드의 고정 표시 정보(제목/이미지/톤 등). 랭킹 데이터(records/myRank)는 API 응답으로 채운다. */
export type WeeklyRankingGamePreset = Omit<
  WeeklyRankingGame,
  'records' | 'myRank'
>

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
