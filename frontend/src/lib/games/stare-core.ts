/**
 * 눈싸움 게임 로직.
 *
 * `ai_game` 프로토타입의 `stare-core.js`를 이식하면서 기획 확정본에 맞춰 두 가지를 바꿨다.
 *
 * 1. **패배 조건**: 프로토타입은 `BOTH_CLOSED`(양쪽 다 감음)일 때만 패배 처리하고
 *    `LEFT_CLOSED`/`RIGHT_CLOSED`(한쪽만 감음)는 경고만 띄웠다. 기획서는 "한쪽 눈이라도 감기면
 *    바로 패배"라고 명시하므로, `LEFT_CLOSED`/`RIGHT_CLOSED`/`BOTH_CLOSED` 모두 즉시 패배로
 *    바꿨다. `BOTH_HALF_CLOSED`(살짝 감김)는 여전히 경고만 준다 — 눈 떨림/깜빡임 시작 단계까지
 *    패배로 처리하면 오탐이 너무 잦기 때문이다.
 * 2. **AI 대결 난이도**: 프로토타입은 혼자하기(무제한 생존)만 있었다. 기획서의
 *    "AI 대결 - easy(15초)/normal(30초)/hard(1분)"을 위해 `targetMs`를 받아, 그 시간을 버티면
 *    승리로 끝나는 모드를 추가했다(혼자하기는 `targetMs`가 없으면 기존처럼 무제한 생존 기록).
 */
import type { CombinedEyeState } from '../eye-tracking/eye-engine'

export const FACE_LOST_LOSE_MS = 1000

export const STARE_AI_DURATIONS_MS: Record<'EASY' | 'NORMAL' | 'HARD', number> =
  {
    EASY: 15000,
    NORMAL: 30000,
    HARD: 60000,
  }

export type StareGamePhase = 'ready' | 'running' | 'finished'
export type StareLoseReason =
  'NONE' | 'BOTH_CLOSED' | 'LEFT_CLOSED' | 'RIGHT_CLOSED' | 'FACE_LOST'
export type StareOutcome = 'NONE' | 'WIN' | 'LOSE'

export interface StareGameState {
  phase: StareGamePhase
  /** AI 난이도 모드일 때 이 시간(ms)을 버티면 승리. 혼자하기는 null(무제한, 생존 시간만 기록). */
  targetMs: number | null
  startedAt: number
  endedAt: number
  elapsedMs: number
  faceLostStartedAt: number
  loseReason: StareLoseReason
  /** AI 난이도 모드에서만 의미 있음. 혼자하기는 항상 'NONE'(생존 시간 자체가 기록). */
  outcome: StareOutcome
  warning: string
  message: string
}

export function makeInitialStareState(
  targetMs: number | null = null,
): StareGameState {
  return {
    phase: 'ready',
    targetMs,
    startedAt: 0,
    endedAt: 0,
    elapsedMs: 0,
    faceLostStartedAt: 0,
    loseReason: 'NONE',
    outcome: 'NONE',
    warning: '대기',
    message: '카메라를 켜고 눈을 뜬 상태로 게임을 시작하세요.',
  }
}

export function startStareRound(state: StareGameState, now: number): void {
  state.phase = 'running'
  state.startedAt = now
  state.endedAt = 0
  state.elapsedMs = 0
  state.faceLostStartedAt = 0
  state.loseReason = 'NONE'
  state.outcome = 'NONE'
  state.warning = '정상'
  state.message =
    state.targetMs !== null
      ? '눈을 감지 말고 목표 시간까지 버티세요.'
      : '눈을 감지 말고 버티세요.'
}

export function resetStareRound(state: StareGameState): void {
  Object.assign(state, makeInitialStareState(state.targetMs))
}

export function updateStareRound(
  state: StareGameState,
  now: number,
  frame: { faceDetected: boolean; combinedState: CombinedEyeState },
): StareLoseReason {
  if (state.phase !== 'running') {
    return 'NONE'
  }

  state.elapsedMs = Math.max(now - state.startedAt, 0)

  if (!frame.faceDetected) {
    if (!state.faceLostStartedAt) {
      state.faceLostStartedAt = now
    }
    const lostMs = now - state.faceLostStartedAt
    state.warning = `얼굴 인식 끊김 ${formatDuration(lostMs)}`
    if (lostMs >= FACE_LOST_LOSE_MS) {
      return finishStareRound(state, now, 'FACE_LOST')
    }
    return 'NONE'
  }

  state.faceLostStartedAt = 0

  if (isEyeClosedState(frame.combinedState)) {
    return finishStareRound(state, now, frame.combinedState)
  }

  state.warning = getStateWarning(frame.combinedState)

  // AI 난이도 모드: 목표 시간을 버텨내면 승리로 종료한다.
  if (state.targetMs !== null && state.elapsedMs >= state.targetMs) {
    state.phase = 'finished'
    state.endedAt = now
    state.outcome = 'WIN'
    state.message = 'AI보다 오래 버텨 승리했습니다!'
    return 'NONE'
  }

  return 'NONE'
}

/** BOTH_CLOSED/LEFT_CLOSED/RIGHT_CLOSED — 한쪽이든 양쪽이든 "감음"으로 판정되는 상태. */
function isEyeClosedState(
  combinedState: CombinedEyeState,
): combinedState is 'BOTH_CLOSED' | 'LEFT_CLOSED' | 'RIGHT_CLOSED' {
  return (
    combinedState === 'BOTH_CLOSED' ||
    combinedState === 'LEFT_CLOSED' ||
    combinedState === 'RIGHT_CLOSED'
  )
}

export function finishStareRound(
  state: StareGameState,
  now: number,
  reason: StareLoseReason,
): StareLoseReason {
  state.phase = 'finished'
  state.endedAt = now
  state.elapsedMs = Math.max(now - state.startedAt, 0)
  state.loseReason = reason
  state.outcome = 'LOSE'
  state.message =
    reason === 'FACE_LOST'
      ? '얼굴 인식이 끊겨 패배했습니다.'
      : '눈을 감아 패배했습니다.'
  return reason
}

/**
 * 친구/랜덤 대결 전용: 상대가 먼저 눈을 감아 패배했다는 걸 알게 됐을 때, 내 라운드도 승리로
 * 종료 처리한다. `finishStareRound`는 항상 `outcome: 'LOSE'`로 고정하므로(내가 졌을 때 쓰는
 * 함수), 이 경우엔 쓸 수 없어 별도 함수로 둔다.
 */
export function finishStareRoundAsWinner(
  state: StareGameState,
  now: number,
): void {
  if (state.phase === 'finished') return
  state.phase = 'finished'
  state.endedAt = now
  state.elapsedMs = Math.max(now - state.startedAt, 0)
  state.loseReason = 'NONE'
  state.outcome = 'WIN'
  state.message = '상대가 먼저 눈을 감아 승리했습니다!'
}

export function canStartStareRound(combinedState: CombinedEyeState): boolean {
  return combinedState === 'BOTH_OPEN'
}

export function getStateWarning(combinedState: CombinedEyeState): string {
  if (combinedState === 'BOTH_OPEN') {
    return '정상'
  }
  if (combinedState === 'BOTH_HALF_CLOSED') {
    return '경고: 눈이 작게 감김'
  }
  if (combinedState === 'LEFT_CLOSED') {
    return '경고: 왼쪽 눈 감김'
  }
  if (combinedState === 'RIGHT_CLOSED') {
    return '경고: 오른쪽 눈 감김'
  }
  if (combinedState === 'UNKNOWN') {
    return '경고: 눈 상태 불안정'
  }
  return combinedState
}

export function formatDuration(milliseconds: number): string {
  const safe = Math.max(milliseconds, 0)
  const totalSeconds = Math.floor(safe / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  const centiseconds = Math.floor((safe % 1000) / 10)
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(centiseconds).padStart(2, '0')}`
}
