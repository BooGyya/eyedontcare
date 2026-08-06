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
 *
 * ⚠️ 실제 배포에서 발견된 버그 2건도 여기서 고친다:
 *
 * A. **한쪽 눈 가리기 편법**: `combineEyeStates`는 한쪽 눈이라도 랜드마크 계산에 실패하면
 *    (`NOT_DETECTED` — 손으로 가림, 얼굴을 옆으로 돌려 카메라 시야 밖으로 나감 등) 무조건
 *    `UNKNOWN`을 반환한다. 이건 "눈을 감음"(CLOSED)이 아니라서 기존 로직(`isEyeClosedState`)이
 *    패배로 안 잡았다 — 즉 한쪽 눈을 가리면 그 눈이 "감긴 게 아니라 안 보이는 것"이 되어 패배를
 *    회피할 수 있었다. 그래서 `leftEyeState`/`rightEyeState`를 따로 받아, 한쪽만
 *    `NOT_DETECTED`인 상태가 일정 시간(기본 2초) 이상 이어지면 패배로 처리한다 — 순간적인 인식
 *    흔들림까지 패배로 잡으면 오탐이 잦아서, 얼굴 전체 인식 끊김(FACE_LOST)과 같은 방식으로 유예
 *    시간을 둔다.
 * B. **동시 눈 감김 무승부 오류**: 친구/랜덤 대결에서 두 사람이 거의 동시에 눈을 감으면, 각자
 *    스스로 LOSE로 확정한 뒤 상대의 "네가 이겼다" 알림이 와도 "난 이미 끝났다"며 무시해서 양쪽
 *    다 LOSE로 남았다. `resolveOpponentLoss`가 이제 이걸 처리한다 — 내가 아직 안 끝났으면 그대로
 *    승리(WIN), 내가 이미 진 지 얼마 안 됐으면(허용 오차, 기본 500ms — 네트워크 중계 지연 감안)
 *    LOSE를 DRAW로 승격시킨다.
 */
import type { CombinedEyeState, EyeState } from '../eye-tracking/eye-engine'

export const FACE_LOST_LOSE_MS = 1000
/** 한쪽 눈만 계속 인식이 안 될 때(손으로 가림, 얼굴 회전 등) 패배로 처리하기까지의 유예 시간. */
export const EYE_NOT_DETECTED_LOSE_MS = 2000
/** 상대의 패배 소식을 받았을 때, 내가 이미 진 지 이 시간 안이면 "거의 동시"로 보고 무승부로 승격시킨다. */
export const SIMULTANEOUS_LOSE_TOLERANCE_MS = 500

export const STARE_AI_DURATIONS_MS: Record<'EASY' | 'NORMAL' | 'HARD', number> =
  {
    EASY: 15000,
    NORMAL: 30000,
    HARD: 60000,
  }

export type StareGamePhase = 'ready' | 'running' | 'finished'
export type StareLoseReason =
  | 'NONE'
  | 'BOTH_CLOSED'
  | 'LEFT_CLOSED'
  | 'RIGHT_CLOSED'
  | 'FACE_LOST'
  | 'LEFT_NOT_DETECTED'
  | 'RIGHT_NOT_DETECTED'
export type StareOutcome = 'NONE' | 'WIN' | 'LOSE' | 'DRAW'

export interface StareGameState {
  phase: StareGamePhase
  /** AI 난이도 모드일 때 이 시간(ms)을 버티면 승리. 혼자하기는 null(무제한, 생존 시간만 기록). */
  targetMs: number | null
  startedAt: number
  endedAt: number
  elapsedMs: number
  faceLostStartedAt: number
  /** 왼쪽/오른쪽 눈이 각각 NOT_DETECTED로 처음 감지된 시각(0이면 지금 감지되고 있다는 뜻). */
  leftNotDetectedStartedAt: number
  rightNotDetectedStartedAt: number
  loseReason: StareLoseReason
  /** AI 난이도 모드에서만 의미 있음. 혼자하기는 항상 'NONE'(생존 시간 자체가 기록). */
  outcome: StareOutcome
  warning: string
  message: string
  /** 친구/랜덤 대결에서 내가 패배로 확정된 로컬 시각(performance.now() 기준). 동시 감김을
   * 무승부로 승격시킬지 판단하는 기준이 된다. */
  lostAt: number
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
    leftNotDetectedStartedAt: 0,
    rightNotDetectedStartedAt: 0,
    loseReason: 'NONE',
    outcome: 'NONE',
    warning: '대기',
    message: '카메라를 켜고 눈을 뜬 상태로 게임을 시작하세요.',
    lostAt: 0,
  }
}

export function startStareRound(state: StareGameState, now: number): void {
  state.phase = 'running'
  state.startedAt = now
  state.endedAt = 0
  state.elapsedMs = 0
  state.faceLostStartedAt = 0
  state.leftNotDetectedStartedAt = 0
  state.rightNotDetectedStartedAt = 0
  state.loseReason = 'NONE'
  state.outcome = 'NONE'
  state.warning = '정상'
  state.lostAt = 0
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
  frame: {
    faceDetected: boolean
    combinedState: CombinedEyeState
    leftEyeState: EyeState
    rightEyeState: EyeState
  },
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
    state.leftNotDetectedStartedAt = 0
    state.rightNotDetectedStartedAt = 0
    return finishStareRound(state, now, frame.combinedState)
  }

  // 한쪽 눈 가리기/얼굴 회전 편법 방지: 한쪽 눈만 랜드마크 인식이 계속 안 되는 상태
  // (NOT_DETECTED)가 일정 시간 이상 이어지면, "그 눈을 실질적으로 감춘 것"으로 보고 패배
  // 처리한다. 얼굴 전체가 안 보이는 FACE_LOST와 같은 유예 시간(순간적 흔들림 오탐 방지) 방식이다.
  // 양쪽 다 NOT_DETECTED면 combinedState가 UNKNOWN이 되지만 사실상 얼굴을 잃은 것과 다름없어
  // 어차피 곧 faceDetected=false로 이어지므로 별도 처리하지 않는다.
  const leftLost = frame.leftEyeState === 'NOT_DETECTED'
  const rightLost = frame.rightEyeState === 'NOT_DETECTED'

  if (leftLost && !rightLost) {
    if (!state.leftNotDetectedStartedAt) {
      state.leftNotDetectedStartedAt = now
    }
    state.rightNotDetectedStartedAt = 0
    const lostMs = now - state.leftNotDetectedStartedAt
    state.warning = `경고: 왼쪽 눈 인식 끊김 ${formatDuration(lostMs)}`
    if (lostMs >= EYE_NOT_DETECTED_LOSE_MS) {
      return finishStareRound(state, now, 'LEFT_NOT_DETECTED')
    }
    return 'NONE'
  }

  if (rightLost && !leftLost) {
    if (!state.rightNotDetectedStartedAt) {
      state.rightNotDetectedStartedAt = now
    }
    state.leftNotDetectedStartedAt = 0
    const lostMs = now - state.rightNotDetectedStartedAt
    state.warning = `경고: 오른쪽 눈 인식 끊김 ${formatDuration(lostMs)}`
    if (lostMs >= EYE_NOT_DETECTED_LOSE_MS) {
      return finishStareRound(state, now, 'RIGHT_NOT_DETECTED')
    }
    return 'NONE'
  }

  state.leftNotDetectedStartedAt = 0
  state.rightNotDetectedStartedAt = 0
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
  state.lostAt = now
  state.message =
    reason === 'FACE_LOST'
      ? '얼굴 인식이 끊겨 패배했습니다.'
      : reason === 'LEFT_NOT_DETECTED' || reason === 'RIGHT_NOT_DETECTED'
        ? '한쪽 눈이 오래 인식되지 않아 패배했습니다.'
        : '눈을 감아 패배했습니다.'
  return reason
}

/**
 * 친구/랜덤 대결 전용: 상대가 눈을 감았다는(또는 인식이 끊겼다는) 소식을 받았을 때 호출한다.
 *
 * - 내가 아직 안 끝났으면(running) 그대로 승리(WIN)로 종료한다.
 * - 내가 이미 진 지 `toleranceMs`(기본 {@link SIMULTANEOUS_LOSE_TOLERANCE_MS}) 이내라면 —
 *   네트워크 중계 지연을 감안했을 때 "거의 동시에 감았다"고 보고, 그 패배를 무승부로 승격시킨다.
 * - 그보다 오래전에 이미 졌다면(진짜로 내가 먼저 졌던 것) 그대로 LOSE를 유지한다 — 나중에 온
 *   "상대도 졌다"는 소식은 무시한다.
 *
 * `finishStareRound`는 항상 `outcome: 'LOSE'`로 고정하므로(내가 졌을 때 쓰는 함수) 이 경우엔
 * 쓸 수 없어 별도 함수로 둔다.
 */
export function resolveOpponentLoss(
  state: StareGameState,
  now: number,
  toleranceMs: number = SIMULTANEOUS_LOSE_TOLERANCE_MS,
): void {
  if (state.phase !== 'finished') {
    state.phase = 'finished'
    state.endedAt = now
    state.elapsedMs = Math.max(now - state.startedAt, 0)
    state.loseReason = 'NONE'
    state.outcome = 'WIN'
    state.message = '상대가 먼저 눈을 감아 승리했습니다!'
    return
  }

  if (state.outcome === 'LOSE' && now - state.lostAt <= toleranceMs) {
    state.outcome = 'DRAW'
    state.message = '두 사람이 거의 동시에 눈을 감아 무승부입니다.'
  }
  // toleranceMs를 넘겼으면(진짜로 내가 먼저 졌던 것) 아무것도 바꾸지 않고 그대로 둔다.
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
