export type GameDetailId = 'air' | 'hold' | 'draw' | 'wave' | 'blink'

export type GamePlayMode = {
  id: 'solo' | 'friends' | 'random' | 'ai'
  label: string
  description: string
}

export type GameDetail = {
  id: GameDetailId
  title: string
  subtitle: string
  image: string
  mascotImage: string
  people: string
  duration: string
  tags: string[]
  steps: string[]
  modes: GamePlayMode[]
}
