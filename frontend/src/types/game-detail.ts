export type GameDetailId = 'air' | 'hold' | 'draw' | 'rhythm' | 'blink'

export type GamePlayMode = {
  id: 'solo' | 'friends' | 'random' | 'ai'
  label: string
  description: string
  badge?: string
}

export type GameGuideColor = 'green' | 'purple' | 'orange' | 'blue' | 'pink'

export type GameGuideCard = {
  title: string
  suffix?: string
  color: GameGuideColor
  icon:
    | 'timer'
    | 'trophy'
    | 'gift'
    | 'star'
    | 'heart'
    | 'robot'
    | 'rounds'
    | 'rhythm'
    | 'combo'
    | 'hearts'
  iconText?: string
  description: string
  badge?: string
  badgeColor?: GameGuideColor
}

export type GameGuideEvent = {
  icon: 'clock' | 'star' | 'wink'
  color: GameGuideColor
  label: string
}

export type GameGuideNotes = {
  title: string
  items: string[]
}

export type GameGuideHighlight = {
  icon: 'trophy' | 'timer' | 'eye' | 'goal'
  text: string
}

export type GameGuideDifficultyItem = {
  label: string
  duration: string
  color: GameGuideColor
}

export type GameGuideDifficulties = {
  title: string
  items: GameGuideDifficultyItem[]
}

export type GameGuideFormulaPart = {
  label: string
  color: GameGuideColor
  icon: 'star' | 'flame' | 'heart'
}

export type GameGuideFormula = {
  title: string
  parts: GameGuideFormulaPart[]
  total: string
}

export type GameGuide = {
  intro: string[]
  cards?: GameGuideCard[]
  stepIcons?: Array<
    | 'eye'
    | 'tally'
    | 'trophy'
    | 'gift'
    | 'pencil'
    | 'clock'
    | 'space'
    | 'mouse'
    | 'list'
    | 'robot'
    | 'note'
    | 'heartbreak'
    | 'flame'
  >
  events?: GameGuideEvent[]
  notes?: GameGuideNotes
  highlights?: GameGuideHighlight[]
  difficulties?: GameGuideDifficulties
  formula?: GameGuideFormula
}

export type GameDetail = {
  id: GameDetailId
  title: string
  subtitle: string
  image: string
  mascotImage: string
  artCaption?: string
  people: string
  duration: string
  durationLabel?: string
  tags: string[]
  steps: string[]
  modes: GamePlayMode[]
  guide?: GameGuide
}
