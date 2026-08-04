/**
 * 눈 깜빡이기 게임 로직.
 *
 * `ai_game` 프로토타입의 `blink-core.js`를 그대로 이식했다 — 기획 확정본의 "제한 시간 20초 안에
 * 눈을 많이 깜빡이면 되는 게임"과 정확히 일치해 규칙 변경 없이 포팅했다.
 *
 * `eye-engine.ts`가 내보내는 `EyeEvent`를 매 프레임 `applyBlinkEvent`에 넣어주기만 하면 된다.
 */
import type { EyeEventType } from '../eye-tracking/eye-engine'

export const BLINK_GAME_SECONDS = 20
export const BLINK_SCORE_UNIT = 10

export type BlinkGamePhase = 'ready' | 'running' | 'finished'

export interface BlinkGameState {
  phase: BlinkGamePhase
  durationMs: number
  startedAt: number
  endsAt: number
  remainingMs: number
  blinkCount: number
  fastBlinkCount: number
  doubleBlinkCount: number
  longCloseCount: number
  winkCount: number
  faceLostCount: number
  score: number
  lastEventType: EyeEventType | 'NONE'
  message: string
}

export function makeInitialBlinkState(
  durationSeconds = BLINK_GAME_SECONDS,
): BlinkGameState {
  const durationMs = Math.max(1, durationSeconds) * 1000
  return {
    phase: 'ready',
    durationMs,
    startedAt: 0,
    endsAt: 0,
    remainingMs: durationMs,
    blinkCount: 0,
    fastBlinkCount: 0,
    doubleBlinkCount: 0,
    longCloseCount: 0,
    winkCount: 0,
    faceLostCount: 0,
    score: 0,
    lastEventType: 'NONE',
    message: '카메라를 켜고 시작하면 20초 동안 깜빡임을 셉니다.',
  }
}

export function startBlinkRound(state: BlinkGameState, now: number): void {
  state.phase = 'running'
  state.startedAt = now
  state.endsAt = now + state.durationMs
  state.remainingMs = state.durationMs
  state.blinkCount = 0
  state.fastBlinkCount = 0
  state.doubleBlinkCount = 0
  state.longCloseCount = 0
  state.winkCount = 0
  state.faceLostCount = 0
  state.score = 0
  state.lastEventType = 'NONE'
  state.message = '게임 진행 중'
}

export function resetBlinkRound(state: BlinkGameState): void {
  Object.assign(state, makeInitialBlinkState(state.durationMs / 1000))
}

/** @returns 이번 호출로 게임이 막 끝났으면 true */
export function updateBlinkTimer(state: BlinkGameState, now: number): boolean {
  if (state.phase !== 'running') {
    return false
  }

  state.remainingMs = Math.max(state.endsAt - now, 0)
  if (state.remainingMs > 0) {
    return false
  }

  state.phase = 'finished'
  state.message = `게임 종료 · ${state.blinkCount}회`
  return true
}

/** @returns 이번 이벤트가 점수에 반영됐으면 true */
export function applyBlinkEvent(
  state: BlinkGameState,
  event: { type: EyeEventType },
): boolean {
  if (state.phase !== 'running') {
    return false
  }

  state.lastEventType = event.type
  if (isCountableBlinkEvent(event.type)) {
    state.blinkCount += 1
    if (event.type === 'FAST_BLINK') {
      state.fastBlinkCount += 1
    }
    if (event.type === 'DOUBLE_BLINK') {
      state.doubleBlinkCount += 1
    }
    state.score = state.blinkCount * BLINK_SCORE_UNIT
    return true
  }

  if (event.type === 'LONG_CLOSE') {
    state.longCloseCount += 1
  } else if (event.type === 'LEFT_WINK' || event.type === 'RIGHT_WINK') {
    state.winkCount += 1
  } else if (event.type === 'FACE_LOST') {
    state.faceLostCount += 1
  }
  return false
}

export function isCountableBlinkEvent(type: EyeEventType): boolean {
  return type === 'BLINK' || type === 'FAST_BLINK' || type === 'DOUBLE_BLINK'
}

export function getBlinkRatePerSecond(state: BlinkGameState): number {
  const elapsedMs =
    state.phase === 'running'
      ? Math.max(state.durationMs - state.remainingMs, 1)
      : Math.max(state.durationMs, 1)
  return state.blinkCount / (elapsedMs / 1000)
}

export function formatRemainingTime(milliseconds: number): string {
  const total = Math.max(Math.ceil(milliseconds / 1000), 0)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
