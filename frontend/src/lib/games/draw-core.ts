/**
 * 눈으로 그리기 게임 로직.
 *
 * `ai_game` 프로토타입(`main.js`의 draw 관련 부분)을 이식하면서 기획 확정본에 맞춰 바꿨다.
 *
 * 1. **난이도별 제시어**: 프로토타입은 24개 단어를 그냥 섞어서 뽑았다. 기획서는 "제시어는
 *    난이도에 따라 분류되어 같은 난이도 카테고리의 제시어는 동일한 점수가 부여돼요" /
 *    "라운드가 올라갈수록 그리기 난이도가 올라가요"라고 명시하므로, 24개 단어를 EASY/MEDIUM/HARD
 *    3단계로 나누고 라운드 1→2→3이 그 순서로 진행되게 했다.
 * 2. **난이도별 기본 점수**: 기획서는 "같은 난이도는 동일한 점수"라고만 하고 정확한 액수는 안
 *    정했다. 프로토타입의 기본 점수(정답 100점)를 EASY 기준으로 삼고, MEDIUM/HARD는 그보다 높게
 *    잡았다 — 필요하면 나중에 쉽게 조정할 수 있도록 상수 하나로 뺐다.
 * 3. **점수 공식 자체**(시간 보너스, AI confidence 보너스)는 프로토타입 그대로 유지했다 —
 *    기획서의 "AI 채점 방식" 설명과 정확히 일치한다.
 */

export type DrawDifficulty = 'EASY' | 'MEDIUM' | 'HARD'

export const DRAWING_TOTAL_ROUNDS = 3
export const DRAWING_ROUND_SECONDS = 100
export const DRAWING_ROUND_DURATION_MS = DRAWING_ROUND_SECONDS * 1000

/** 라운드 1→2→3이 이 순서로 진행된다(라운드가 올라갈수록 난이도 상승). */
export const DRAWING_ROUND_DIFFICULTY: readonly DrawDifficulty[] =
  Object.freeze(['EASY', 'MEDIUM', 'HARD'])

export const DRAWING_BASE_SCORE: Record<DrawDifficulty, number> = {
  EASY: 100,
  MEDIUM: 150,
  HARD: 200,
}

export const DRAWING_DIFFICULTY_LABEL: Record<DrawDifficulty, string> = {
  EASY: '쉬움',
  MEDIUM: '보통',
  HARD: '어려움',
}

export const DRAWING_WORDS_BY_DIFFICULTY: Record<
  DrawDifficulty,
  readonly string[]
> = {
  EASY: Object.freeze(['하트', '별', '해', '달', '컵', '구름', '사과', '열쇠']),
  MEDIUM: Object.freeze([
    '나무',
    '집',
    '꽃',
    '안경',
    '시계',
    '우산',
    '의자',
    '물고기',
  ]),
  HARD: Object.freeze([
    '자동차',
    '배',
    '비행기',
    '기타',
    '왕관',
    '산',
    '얼굴',
    '책',
  ]),
}

export const DRAWING_ALL_WORDS: readonly string[] = Object.freeze(
  (['EASY', 'MEDIUM', 'HARD'] as DrawDifficulty[]).flatMap(
    (difficulty) => DRAWING_WORDS_BY_DIFFICULTY[difficulty],
  ),
)

export interface DrawPoint {
  x: number
  y: number
}

export interface DrawStroke {
  points: DrawPoint[]
  color: string
  width: number
}

export interface VisionRecognition {
  label: string
  confidence: number
  isTarget: boolean
  reason: string
  candidates: string[]
  model?: string
}

export interface DrawRoundResult {
  round: number
  difficulty: DrawDifficulty
  prompt: string
  aiGuess: string
  confidence: number
  answer: string
  aiCorrect: boolean
  answerCorrect: boolean
  success: boolean
  baseScore: number
  timeBonus: number
  confidenceBonus: number
  score: number
  reason: string
}

export type DrawGamePhase =
  'ready' | 'running' | 'judging' | 'round-finished' | 'finished'

export interface DrawGameState {
  phase: DrawGamePhase
  round: number
  prompt: string
  difficulty: DrawDifficulty
  remainingMs: number
  score: number
  errorMessage: string
  history: DrawRoundResult[]
}

/** 라운드 1~3에 쓸 제시어를 난이도 순서대로(EASY→MEDIUM→HARD) 하나씩 무작위로 뽑는다. */
export function pickWordsForGame(random: () => number = Math.random): string[] {
  return DRAWING_ROUND_DIFFICULTY.map((difficulty) => {
    const words = DRAWING_WORDS_BY_DIFFICULTY[difficulty]
    const index = Math.floor(random() * words.length) % words.length
    return words[index]
  })
}

export function makeInitialDrawGameState(): DrawGameState {
  return {
    phase: 'ready',
    round: 0,
    prompt: '',
    difficulty: 'EASY',
    remainingMs: DRAWING_ROUND_DURATION_MS,
    score: 0,
    errorMessage: '',
    history: [],
  }
}

/** 다음 라운드를 시작한다. `words`는 `pickWordsForGame()`으로 미리 뽑아 둔 3개짜리 배열. */
export function startDrawRound(
  state: DrawGameState,
  words: readonly string[],
): void {
  state.round += 1
  state.difficulty = DRAWING_ROUND_DIFFICULTY[state.round - 1] ?? 'HARD'
  state.prompt = words[state.round - 1] ?? words[words.length - 1] ?? ''
  state.remainingMs = DRAWING_ROUND_DURATION_MS
  state.phase = 'running'
  state.errorMessage = ''
}

/** 타이머 틱. 남은 시간이 0이 되면 true를 반환한다(시간 종료로 라운드를 마쳐야 함). */
export function tickDrawRoundTimer(
  state: DrawGameState,
  deltaMs: number,
): boolean {
  if (state.phase !== 'running') return false
  state.remainingMs = Math.max(state.remainingMs - deltaMs, 0)
  return state.remainingMs <= 0
}

export function beginJudging(state: DrawGameState): void {
  state.phase = 'judging'
  state.errorMessage = ''
}

/**
 * AI 판정 결과를 받아 이번 라운드 점수를 계산하고 히스토리에 기록한다.
 * 점수 공식은 프로토타입 그대로: 기본점수(난이도별) + 시간 보너스 + AI confidence 보너스.
 * AI가 못 맞혔어도 직접 입력한 정답이 맞으면 70% 부분점수를 준다.
 */
export function applyDrawRoundResult(
  state: DrawGameState,
  recognition: VisionRecognition,
  typedAnswer: string,
): DrawRoundResult {
  const aiCorrect =
    recognition.isTarget ||
    normalizeAnswer(recognition.label) === normalizeAnswer(state.prompt)
  const answerCorrect =
    normalizeAnswer(typedAnswer) === normalizeAnswer(state.prompt)
  const baseScore = DRAWING_BASE_SCORE[state.difficulty]
  const remainingSeconds = state.remainingMs / 1000
  const earnedBase = aiCorrect
    ? baseScore
    : answerCorrect
      ? Math.round(baseScore * 0.7)
      : 0
  const timeBonus = earnedBase > 0 ? Math.round(remainingSeconds * 1.2) : 0
  const confidenceBonus = aiCorrect
    ? Math.round(recognition.confidence * 30)
    : 0
  const roundScore = earnedBase + timeBonus + confidenceBonus

  const result: DrawRoundResult = {
    round: state.round,
    difficulty: state.difficulty,
    prompt: state.prompt,
    aiGuess: recognition.label,
    confidence: recognition.confidence,
    answer: typedAnswer,
    aiCorrect,
    answerCorrect,
    success: aiCorrect || answerCorrect,
    baseScore: earnedBase,
    timeBonus,
    confidenceBonus,
    score: roundScore,
    reason: recognition.reason,
  }

  state.score += roundScore
  state.history.push(result)
  state.phase =
    state.round >= DRAWING_TOTAL_ROUNDS ? 'finished' : 'round-finished'
  return result
}

/** AI 판정 요청이 실패했을 때(네트워크 오류 등) — 라운드를 다시 진행 중 상태로 되돌린다. */
export function reportDrawJudgingError(
  state: DrawGameState,
  message: string,
): void {
  state.errorMessage = message
  state.phase = 'running'
}

export function isDrawGameFinished(state: DrawGameState): boolean {
  return state.phase === 'finished'
}

/**
 * 시선 좌표(0~1)를 획에 추가한다. `ai_game` 프로토타입의 `addPointToStroke`를 그대로 이식했다 —
 * 이전 점과 너무 멀면(0.22 초과) 새 획으로 끊고(깜빡임 직후엔 `allowBridge`로 이어붙임), 너무
 * 가까우면(0.0025 미만) 손떨림으로 보고 무시한다.
 */
export function addPointToStroke(
  strokes: DrawStroke[],
  activeStroke: DrawStroke | null,
  point: DrawPoint,
  options: { color: string; width: number; allowBridge?: boolean },
): DrawStroke | null {
  let stroke = activeStroke
  const previous = stroke?.points.at(-1)
  if (previous && !options.allowBridge && distance(previous, point) > 0.22) {
    stroke = null
  }

  if (!stroke) {
    stroke = { points: [], color: options.color, width: options.width }
    strokes.push(stroke)
  }

  const currentPrevious = stroke.points.at(-1)
  if (currentPrevious && distance(currentPrevious, point) < 0.0025) {
    return stroke
  }
  stroke.points.push(point)
  return stroke
}

function distance(a: DrawPoint, b: DrawPoint): number {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

export function normalizeAnswer(text: string): string {
  return text.trim().toLowerCase().replace(/\s+/g, '')
}
