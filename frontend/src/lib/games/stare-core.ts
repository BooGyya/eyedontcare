/**
 * 눈싸움 게임 로직.
 *
 * `ai_game` 프로토타입의 `stare-core.js`를 이식하면서 기획 확정본에 맞춰 두 가지를 바꿨다.
 *
 * 1. **패배 조건**: 프로토타입은 `BOTH_CLOSED`(양쪽 다 감음)일 때만 패배 처리하고
 *    `LEFT_CLOSED`/`RIGHT_CLOSED`(한쪽만 감음)는 경고만 띄웠다. 기획서는 "한쪽 눈이라도 감기면
 *    바로 패배"라고 명시하므로, `LEFT_CLOSED`/`RIGHT_CLOSED`/`BOTH_CLOSED` 모두 즉시 패배로
 *    바꿨다.
 * 2. **AI 대결 난이도**: 프로토타입은 혼자하기(무제한 생존)만 있었다. 기획서의
 *    "AI 대결 - easy(15초)/normal(30초)/hard(1분)"을 위해 `targetMs`를 받아, 그 시간을 버티면
 *    승리로 끝나는 모드를 추가했다(혼자하기는 `targetMs`가 없으면 기존처럼 무제한 생존 기록).
 *
 * ⚠️ 실제 배포에서 발견된 버그 2건도 여기서 고친다:
 *
 * A. **한쪽 눈 가리기 편법**: 원래는 한쪽 눈을 가려도 아무 반응이 없어 패배를 회피할 수 있었다.
 *
 *    ⚠️ 진짜 원인은 이 파일이 아니라 카메라 설정에 있었다 — `useLocalCamera.ts`가 해상도를
 *    지정하지 않고 `getUserMedia({ video: true })`만 호출해서 브라우저 기본 해상도(보통
 *    640x480)로 잡히고 있었다. 그 화질에서는 눈 주변 랜드마크가 뭉개져 EAR(눈 세로/가로 비율)
 *    계산이 매우 부정확해지고, 좌우 눈을 따로 구분하지 못한다. 연동 전 프로토타입은 1280x720을
 *    요청했는데 이식 과정에서 이 설정이 누락된 것이 실제 원인이었다(해당 파일에 회귀 테스트를
 *    두었다). 해상도를 복구하면 한쪽 눈을 가렸을 때 그 눈이 정상적으로 `CLOSED`로 분류되어
 *    아래 `LEFT_CLOSED`/`RIGHT_CLOSED` 즉시 패배 경로로 잡힌다.
 *
 *    그 위에 안전망을 하나 더 둔다: 어떤 상태로 분류되든 상관없이, **"명확하게 양쪽 다 뜬 상태
 *    (`BOTH_OPEN`)가 아닌 모든 상태"**가 일정 시간(기본 2초) 이상 지속되면 패배로 처리한다.
 *    `BOTH_HALF_CLOSED`/`UNKNOWN`처럼 즉시 패배는 아닌 애매한 상태로 계속 버티는 것도 막기
 *    위함이다 — 순간적인 깜빡임/흔들림은 2초 안에 끝나니 오탐이 되지 않고, 얼굴을 돌리거나
 *    카메라를 가리는 건 2초 넘게 유지되니 잡힌다.
 * B. **동시 눈 감김 무승부 오류**: 친구/랜덤 대결에서 두 사람이 거의 동시에 눈을 감으면, 각자
 *    스스로 LOSE로 확정한 뒤 상대의 "네가 이겼다" 알림이 와도 "난 이미 끝났다"며 무시해서 양쪽
 *    다 LOSE로 남았다. `resolveOpponentLoss`가 이제 이걸 처리한다 — 내가 아직 안 끝났으면 그대로
 *    승리(WIN), 내가 이미 진 지 얼마 안 됐으면(허용 오차, 기본 500ms — 네트워크 중계 지연 감안)
 *    LOSE를 DRAW로 승격시킨다.
 */
import type { CombinedEyeState } from '../eye-tracking/eye-engine'

export const FACE_LOST_LOSE_MS = 1000
/**
 * "명확하게 양쪽 다 뜬 상태(BOTH_OPEN)가 아닌" 애매한 상태(한쪽 눈 가리기, 얼굴 회전,
 * BOTH_HALF_CLOSED, UNKNOWN 등)가 이 시간 이상 지속되면 패배로 처리한다.
 */
export const UNCLEAR_EYE_STATE_LOSE_MS = 2000
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
  | 'UNCLEAR_EYE_STATE'
export type StareOutcome = 'NONE' | 'WIN' | 'LOSE' | 'DRAW'

export interface StareGameState {
  phase: StareGamePhase
  /** AI 난이도 모드일 때 이 시간(ms)을 버티면 승리. 혼자하기는 null(무제한, 생존 시간만 기록). */
  targetMs: number | null
  startedAt: number
  endedAt: number
  elapsedMs: number
  faceLostStartedAt: number
  /** BOTH_OPEN이 아닌 애매한 상태가 처음 시작된 시각(0이면 지금은 명확히 정상이라는 뜻). */
  unclearStateStartedAt: number
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
    unclearStateStartedAt: 0,
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
  state.unclearStateStartedAt = 0
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
  frame: { faceDetected: boolean; combinedState: CombinedEyeState },
): StareLoseReason {
  if (state.phase !== 'running') {
    return 'NONE'
  }

  state.elapsedMs = Math.max(now - state.startedAt, 0)

  if (!frame.faceDetected) {
    state.unclearStateStartedAt = 0
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
    state.unclearStateStartedAt = 0
    return finishStareRound(state, now, frame.combinedState)
  }

  if (frame.combinedState !== 'BOTH_OPEN') {
    // 안전망: "명확히 양쪽 다 뜬 상태"가 아닌 애매한 상태(BOTH_HALF_CLOSED, UNKNOWN)가 일정
    // 시간 이상 이어지면 패배 처리한다. 순간적인 흔들림 오탐 방지를 위해 얼굴 인식
    // 끊김(FACE_LOST)과 같은 방식으로 유예 시간을 둔다.
    if (!state.unclearStateStartedAt) {
      state.unclearStateStartedAt = now
    }
    const unclearMs = now - state.unclearStateStartedAt
    state.warning = `${getStateWarning(frame.combinedState)} ${formatDuration(unclearMs)}`
    if (unclearMs >= UNCLEAR_EYE_STATE_LOSE_MS) {
      return finishStareRound(state, now, 'UNCLEAR_EYE_STATE')
    }
    return 'NONE'
  }

  state.unclearStateStartedAt = 0
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
      : reason === 'UNCLEAR_EYE_STATE'
        ? '눈 상태가 오래 확인되지 않아 패배했습니다.'
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
