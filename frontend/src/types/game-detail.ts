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

/**
 * AI 대결 난이도를 실제로 선택할 때 쓰는 값 — {@link GameGuideDifficulties}는 안내 문구용
 * 정적 표시일 뿐이라 실제 라우팅에 쓸 machine-readable 값(value)이 없다. 이 타입은 그 값을
 * 담아 실제 게임 시작 라우팅(`?difficulty=easy`)에 쓰기 위한 것이다.
 */
export type GameAiDifficultyOption = {
  value: 'easy' | 'normal' | 'hard'
  label: string
  duration: string
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
  /** AI 대결 난이도가 있는 게임만 채운다(지금은 눈싸움뿐). 없으면 AI 모드는 난이도 선택 없이 바로 시작. */
  aiDifficulties?: GameAiDifficultyOption[]
}
