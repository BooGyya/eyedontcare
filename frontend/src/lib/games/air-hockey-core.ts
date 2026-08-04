/**
 * 에어하키 게임 로직(물리 엔진).
 *
 * `ai_game` 프로토타입의 `air-hockey-core.js`를 이식했다. 말렛/퍽 충돌·서브·발사 물리는 그대로
 * 가져왔고, 종료 조건만 기획 확정본에 맞춰 바꿨다.
 *
 * ⚠️ **규칙 변경**: 프로토타입은 `TARGET_SCORE`(5점) 선취 시 종료했지만, 기획서는 "게임 시간은
 * 1분"이라고 명시한다(선취 목표점수 없음). 그래서 점수 자체는 그대로 "골 1개 = 1점"으로 두되,
 * 게임을 끝내는 조건을 시간 기반으로 바꿨다 — `updateAirHockeyMatch`가 매 프레임 남은 시간을
 * 갱신하고, 시간이 다 되면 최종 점수를 비교해 승자를 정한다. `TARGET_SCORE`는 혹시 다른 곳에서
 * 참조할 수 있어 상수는 남겨뒀지만, 더 이상 종료 조건으로 쓰지 않는다.
 */

export const AIR_HOCKEY_WIDTH = 720
export const AIR_HOCKEY_HEIGHT = 900
/** @deprecated 더 이상 종료 조건으로 쓰지 않는다 — 게임 시간은 AIR_HOCKEY_MATCH_DURATION_MS로 관리한다. */
export const TARGET_SCORE = 5
/** 기획 확정본: "게임 시간은 1분". */
export const AIR_HOCKEY_MATCH_DURATION_MS = 60000

export type MalletSide = 'top' | 'bottom'

export interface Mallet {
  side: MalletSide
  x: number
  lastX: number
  targetX: number
  y: number
  r: number
  score: number
  source: 'AI' | 'None' | 'Player'
  boostUntil: number
  lastActionAt: number
}

export interface Puck {
  x: number
  y: number
  r: number
  vx: number
  vy: number
  held: boolean
  speed: number
  lastHitBy: MalletSide | null
}

export interface AirHockeyState {
  running: boolean
  paused: boolean
  gameOver: boolean
  message: string
  server: MalletSide
  lastGoalAt: number
  startedAt: number
  endsAt: number
  remainingMs: number
  winner: MalletSide | 'draw' | null
  top: Mallet
  bottom: Mallet
  puck: Puck
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

export function circleCircleOverlap(
  a: { x: number; y: number; r: number },
  b: { x: number; y: number; r: number },
): boolean {
  const dx = a.x - b.x
  const dy = a.y - b.y
  const radius = a.r + b.r
  return dx * dx + dy * dy <= radius * radius
}

export function makeInitialAirHockeyState(): AirHockeyState {
  return {
    running: false,
    paused: false,
    gameOver: false,
    message: '눈으로 말렛을 움직이세요. 깜빡이거나 Space로 퍽을 발사합니다.',
    server: 'bottom',
    lastGoalAt: 0,
    startedAt: 0,
    endsAt: 0,
    remainingMs: AIR_HOCKEY_MATCH_DURATION_MS,
    winner: null,
    top: makeMallet('top'),
    bottom: makeMallet('bottom'),
    puck: makePuck(),
  }
}

export function makeMallet(side: MalletSide): Mallet {
  const y = side === 'top' ? 138 : AIR_HOCKEY_HEIGHT - 138
  return {
    side,
    x: AIR_HOCKEY_WIDTH / 2,
    lastX: AIR_HOCKEY_WIDTH / 2,
    targetX: AIR_HOCKEY_WIDTH / 2,
    y,
    r: 43,
    score: 0,
    source: side === 'top' ? 'AI' : 'None',
    boostUntil: 0,
    lastActionAt: 0,
  }
}

export function makePuck(): Puck {
  return {
    x: AIR_HOCKEY_WIDTH / 2,
    y: AIR_HOCKEY_HEIGHT / 2,
    r: 16,
    vx: 0,
    vy: 0,
    held: true,
    speed: 430,
    lastHitBy: null,
  }
}

export function resetPuckForServe(state: AirHockeyState): void {
  state.puck = makePuck()
  state.puck.held = true
  state.puck.x = state[state.server].x
  state.puck.y =
    state[state.server].side === 'bottom'
      ? state.bottom.y - 68
      : state.top.y + 68
}

export function launchPuck(state: AirHockeyState, side: MalletSide): void {
  if (!state.puck.held) {
    return
  }
  state.running = true
  state.puck.held = false
  state.server = side
  const mallet = state[side]
  const direction = side === 'bottom' ? -1 : 1
  const offset = clamp(
    (state.puck.x - AIR_HOCKEY_WIDTH / 2) / (AIR_HOCKEY_WIDTH / 2),
    -1,
    1,
  )
  state.puck.vx = offset * 160 + (mallet.x - mallet.lastX) * 8
  state.puck.vy = direction * state.puck.speed
  state.message = '퍽에서 눈을 떼지 마세요.'
}

export function applyStrike(
  state: AirHockeyState,
  side: MalletSide,
  now: number,
): boolean {
  const mallet = state[side]
  if (now - mallet.lastActionAt < 420) {
    return false
  }
  mallet.lastActionAt = now
  mallet.boostUntil = now + 700
  if (state.puck.held) {
    launchPuck(state, side)
    return true
  }

  const isInHalf =
    side === 'bottom'
      ? state.puck.y > AIR_HOCKEY_HEIGHT / 2
      : state.puck.y < AIR_HOCKEY_HEIGHT / 2
  if (!isInHalf) {
    return true
  }

  const direction = side === 'bottom' ? -1 : 1
  state.puck.vx += clamp((state.puck.x - mallet.x) * 2.2, -180, 180)
  state.puck.vy = direction * Math.max(Math.abs(state.puck.vy), 560)
  state.puck.lastHitBy = side
  return true
}

export function resolveMalletCollision(
  state: AirHockeyState,
  side: MalletSide,
): boolean {
  const mallet = state[side]
  const puck = state.puck
  if (!circleCircleOverlap(mallet, puck)) {
    return false
  }

  const dx = puck.x - mallet.x
  const dy = puck.y - mallet.y
  const dist = Math.max(Math.hypot(dx, dy), 0.001)
  const nx = dx / dist
  const ny = dy / dist
  const minDistance = mallet.r + puck.r + 0.5
  puck.x = mallet.x + nx * minDistance
  puck.y = mallet.y + ny * minDistance

  const malletVelocity = mallet.x - mallet.lastX
  const baseSpeed = Math.min(
    Math.max(Math.hypot(puck.vx, puck.vy), puck.speed) + 42,
    820,
  )
  puck.vx = nx * baseSpeed + malletVelocity * 15
  puck.vy = ny * baseSpeed
  if (side === 'bottom') {
    puck.vy = -Math.abs(puck.vy)
  } else {
    puck.vy = Math.abs(puck.vy)
  }
  puck.lastHitBy = side
  return true
}

export function getGoalResult(
  state: AirHockeyState,
): MalletSide | 'wall' | null {
  const goalHalfWidth = 138
  const inGoalMouth =
    Math.abs(state.puck.x - AIR_HOCKEY_WIDTH / 2) <= goalHalfWidth
  if (state.puck.y - state.puck.r <= 0) {
    return inGoalMouth ? 'bottom' : 'wall'
  }
  if (state.puck.y + state.puck.r >= AIR_HOCKEY_HEIGHT) {
    return inGoalMouth ? 'top' : 'wall'
  }
  return null
}

/** 골이 들어갔을 때 점수만 반영한다(골 1개 = 1점). 게임 종료 여부는 더 이상 여기서 판단하지 않는다. */
export function scoreGoal(
  state: AirHockeyState,
  scoringSide: MalletSide,
  now: number,
): void {
  state[scoringSide].score += 1
  state.lastGoalAt = now
  state.server = scoringSide === 'bottom' ? 'top' : 'bottom'
  state.message = `${scoringSide === 'bottom' ? 'P1' : 'P2'} 득점! 눈을 깜빡이거나 Space로 서브하세요.`
  resetPuckForServe(state)
}

export function startAirHockeyMatch(state: AirHockeyState, now: number): void {
  state.running = true
  state.gameOver = false
  state.paused = false
  state.winner = null
  state.startedAt = now
  state.endsAt = now + AIR_HOCKEY_MATCH_DURATION_MS
  state.remainingMs = AIR_HOCKEY_MATCH_DURATION_MS
  state.top.score = 0
  state.bottom.score = 0
  resetPuckForServe(state)
}

/**
 * 매 프레임 호출한다. 1분이 지나면 점수를 비교해 게임을 종료한다.
 * @returns 이번 호출로 게임이 막 끝났으면 true
 */
export function updateAirHockeyMatch(
  state: AirHockeyState,
  now: number,
): boolean {
  if (state.gameOver || !state.running) {
    return false
  }

  state.remainingMs = Math.max(state.endsAt - now, 0)
  if (state.remainingMs > 0) {
    return false
  }

  state.gameOver = true
  state.running = false
  state.puck.held = true
  state.winner = determineAirHockeyWinner(state)
  state.message =
    state.winner === 'draw'
      ? `${state.bottom.score} : ${state.top.score} 무승부로 종료되었습니다.`
      : `${state.winner === 'bottom' ? 'P1' : 'P2'} 승리! (${state.bottom.score} : ${state.top.score})`
  return true
}

export function determineAirHockeyWinner(
  state: AirHockeyState,
): MalletSide | 'draw' {
  if (state.bottom.score === state.top.score) {
    return 'draw'
  }
  return state.bottom.score > state.top.score ? 'bottom' : 'top'
}
