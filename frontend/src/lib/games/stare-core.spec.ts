import { describe, expect, it } from 'vitest'
import type { CombinedEyeState, EyeState } from '../eye-tracking/eye-engine'
import {
  canStartStareRound,
  EYE_NOT_DETECTED_LOSE_MS,
  FACE_LOST_LOSE_MS,
  formatDuration,
  getStateWarning,
  makeInitialStareState,
  resetStareRound,
  resolveOpponentLoss,
  SIMULTANEOUS_LOSE_TOLERANCE_MS,
  STARE_AI_DURATIONS_MS,
  startStareRound,
  updateStareRound,
} from './stare-core'

/**
 * combinedState만으로 대부분의 기존 테스트를 그대로 쓸 수 있게, 왼쪽/오른쪽 눈 상태를
 * combinedState로부터 자동으로 유추해 준다. "한쪽 눈만 인식 안 됨" 같은 새 시나리오는
 * overrides로 직접 지정한다.
 */
function frame(
  faceDetected: boolean,
  combinedState: CombinedEyeState,
  overrides: Partial<{ leftEyeState: EyeState; rightEyeState: EyeState }> = {},
) {
  const [defaultLeft, defaultRight] = combinedStateToEyeStates(combinedState)
  return {
    faceDetected,
    combinedState,
    leftEyeState: overrides.leftEyeState ?? defaultLeft,
    rightEyeState: overrides.rightEyeState ?? defaultRight,
  }
}

function combinedStateToEyeStates(
  combinedState: CombinedEyeState,
): [EyeState, EyeState] {
  switch (combinedState) {
    case 'BOTH_OPEN':
      return ['OPEN', 'OPEN']
    case 'BOTH_CLOSED':
      return ['CLOSED', 'CLOSED']
    case 'BOTH_HALF_CLOSED':
      return ['HALF_CLOSED', 'HALF_CLOSED']
    case 'LEFT_CLOSED':
      return ['CLOSED', 'OPEN']
    case 'RIGHT_CLOSED':
      return ['OPEN', 'CLOSED']
    case 'UNKNOWN':
      return ['NOT_DETECTED', 'NOT_DETECTED']
  }
}

describe('stare-core', () => {
  it('formats durations and only allows starting from BOTH_OPEN', () => {
    expect(formatDuration(0)).toBe('00:00.00')
    expect(formatDuration(65_432)).toBe('01:05.43')
    expect(canStartStareRound('BOTH_OPEN')).toBe(true)
    expect(canStartStareRound('BOTH_HALF_CLOSED')).toBe(false)
  })

  it('regression: 한쪽 눈만 감아도(LEFT_CLOSED) 즉시 패배해야 한다 (기획 확정본)', () => {
    // 프로토타입 원본은 LEFT_CLOSED/RIGHT_CLOSED를 "경고"로만 취급하고 계속 진행시켰다.
    // 기획서는 "한쪽 눈이라도 감기면 바로 패배"라고 명시하므로 이 동작은 반드시 바뀌어야 한다.
    const state = makeInitialStareState()
    startStareRound(state, 1000)

    expect(updateStareRound(state, 1800, frame(true, 'BOTH_OPEN'))).toBe('NONE')
    expect(state.phase).toBe('running')

    expect(updateStareRound(state, 1900, frame(true, 'LEFT_CLOSED'))).toBe(
      'LEFT_CLOSED',
    )
    expect(state.phase).toBe('finished')
    expect(state.loseReason).toBe('LEFT_CLOSED')
    expect(state.outcome).toBe('LOSE')
  })

  it('여전히 BOTH_CLOSED로도 패배한다', () => {
    const state = makeInitialStareState()
    startStareRound(state, 1000)
    updateStareRound(state, 1800, frame(true, 'BOTH_OPEN'))

    expect(updateStareRound(state, 2000, frame(true, 'BOTH_CLOSED'))).toBe(
      'BOTH_CLOSED',
    )
    expect(state.phase).toBe('finished')
    expect(state.loseReason).toBe('BOTH_CLOSED')
    expect(state.elapsedMs).toBe(1000)
  })

  it('BOTH_HALF_CLOSED는 경고만 주고 라운드를 끝내지 않는다', () => {
    const state = makeInitialStareState()
    startStareRound(state, 1000)

    expect(updateStareRound(state, 1500, frame(true, 'BOTH_HALF_CLOSED'))).toBe(
      'NONE',
    )
    expect(state.phase).toBe('running')
    expect(state.warning).toBe('경고: 눈이 작게 감김')
  })

  it('얼굴 인식이 1초 이상 끊기면 패배로 종료한다', () => {
    const state = makeInitialStareState()
    startStareRound(state, 5000)

    expect(updateStareRound(state, 5100, frame(false, 'UNKNOWN'))).toBe('NONE')
    expect(
      updateStareRound(
        state,
        5000 + FACE_LOST_LOSE_MS + 90,
        frame(false, 'UNKNOWN'),
      ),
    ).toBe('NONE')
    expect(
      updateStareRound(
        state,
        5000 + FACE_LOST_LOSE_MS + 110,
        frame(false, 'UNKNOWN'),
      ),
    ).toBe('FACE_LOST')
    expect(state.phase).toBe('finished')
    expect(state.loseReason).toBe('FACE_LOST')
  })

  describe('버그 수정: 한쪽 눈 가리기 편법', () => {
    it('왼쪽 눈만 계속 NOT_DETECTED면(가림/얼굴 회전) 2초 후 패배로 종료한다', () => {
      const state = makeInitialStareState()
      startStareRound(state, 0)

      const notDetected = frame(true, 'UNKNOWN', {
        leftEyeState: 'NOT_DETECTED',
        rightEyeState: 'OPEN',
      })

      // 10ms 시점에 처음 감지가 끊긴다(leftNotDetectedStartedAt = 10).
      expect(updateStareRound(state, 10, notDetected)).toBe('NONE')

      // 2초가 되기 전까지는 경고만 주고 계속 진행되어야 한다(순간적 흔들림 오탐 방지).
      expect(
        updateStareRound(
          state,
          10 + EYE_NOT_DETECTED_LOSE_MS - 100,
          notDetected,
        ),
      ).toBe('NONE')
      expect(state.phase).toBe('running')
      expect(state.warning).toContain('왼쪽 눈 인식 끊김')

      // 2초를 채우면 패배로 종료된다.
      expect(
        updateStareRound(
          state,
          10 + EYE_NOT_DETECTED_LOSE_MS + 50,
          notDetected,
        ),
      ).toBe('LEFT_NOT_DETECTED')
      expect(state.phase).toBe('finished')
      expect(state.loseReason).toBe('LEFT_NOT_DETECTED')
      expect(state.outcome).toBe('LOSE')
    })

    it('오른쪽 눈만 계속 NOT_DETECTED여도 동일하게 2초 후 패배 처리한다', () => {
      const state = makeInitialStareState()
      startStareRound(state, 0)

      const notDetected = frame(true, 'UNKNOWN', {
        leftEyeState: 'OPEN',
        rightEyeState: 'NOT_DETECTED',
      })

      updateStareRound(state, 10, notDetected)
      updateStareRound(state, 10 + EYE_NOT_DETECTED_LOSE_MS - 100, notDetected)
      expect(state.phase).toBe('running')

      expect(
        updateStareRound(
          state,
          10 + EYE_NOT_DETECTED_LOSE_MS + 50,
          notDetected,
        ),
      ).toBe('RIGHT_NOT_DETECTED')
      expect(state.loseReason).toBe('RIGHT_NOT_DETECTED')
    })

    it('2초를 채우기 전에 다시 눈이 인식되면 타이머가 리셋된다', () => {
      const state = makeInitialStareState()
      startStareRound(state, 0)

      updateStareRound(
        state,
        10,
        frame(true, 'UNKNOWN', {
          leftEyeState: 'NOT_DETECTED',
          rightEyeState: 'OPEN',
        }),
      )
      expect(state.leftNotDetectedStartedAt).not.toBe(0)

      // 다시 정상적으로 보이면 타이머가 리셋된다.
      updateStareRound(state, 500, frame(true, 'BOTH_OPEN'))
      expect(state.leftNotDetectedStartedAt).toBe(0)
      expect(state.phase).toBe('running')

      // 리셋된 뒤 새로 끊긴 시점(500)부터 다시 2초를 채워야 한다 — 리셋 전 경과 시간과
      // 합산되지 않는다(예: 리셋 후 100ms만 지나면 아직 패배하지 않아야 함).
      expect(
        updateStareRound(
          state,
          600,
          frame(true, 'UNKNOWN', {
            leftEyeState: 'NOT_DETECTED',
            rightEyeState: 'OPEN',
          }),
        ),
      ).toBe('NONE')
      expect(state.phase).toBe('running')
    })
  })

  it('AI 난이도 모드: 목표 시간을 버티면 승리로 종료한다', () => {
    const state = makeInitialStareState(STARE_AI_DURATIONS_MS.EASY) // 15초
    startStareRound(state, 0)

    // 14.9초까지는 계속 진행 중이어야 한다.
    updateStareRound(state, 14_900, frame(true, 'BOTH_OPEN'))
    expect(state.phase).toBe('running')

    // 15초를 채우면 승리 처리된다.
    updateStareRound(state, 15_000, frame(true, 'BOTH_OPEN'))
    expect(state.phase).toBe('finished')
    expect(state.outcome).toBe('WIN')
  })

  it('혼자하기(targetMs 없음)는 시간 제한 없이 계속 생존 기록만 갱신한다', () => {
    const state = makeInitialStareState(null)
    startStareRound(state, 0)

    updateStareRound(state, 120_000, frame(true, 'BOTH_OPEN'))
    expect(state.phase).toBe('running')
    expect(state.elapsedMs).toBe(120_000)
  })

  it('경고 문구 매핑', () => {
    expect(getStateWarning('BOTH_OPEN')).toBe('정상')
    expect(getStateWarning('RIGHT_CLOSED')).toBe('경고: 오른쪽 눈 감김')
    expect(getStateWarning('UNKNOWN')).toBe('경고: 눈 상태 불안정')
  })

  it('resetStareRound은 targetMs를 유지한 채 초기화한다', () => {
    const state = makeInitialStareState(STARE_AI_DURATIONS_MS.NORMAL)
    startStareRound(state, 1000)
    updateStareRound(state, 2000, frame(true, 'BOTH_CLOSED'))

    resetStareRound(state)
    expect(state.phase).toBe('ready')
    expect(state.targetMs).toBe(STARE_AI_DURATIONS_MS.NORMAL)
  })

  describe('resolveOpponentLoss', () => {
    it('내가 아직 안 끝났으면 상대 패배 소식에 승리로 종료한다', () => {
      const state = makeInitialStareState(null)
      startStareRound(state, 1000)
      updateStareRound(state, 4000, frame(true, 'BOTH_OPEN'))

      resolveOpponentLoss(state, 4200)

      expect(state.phase).toBe('finished')
      expect(state.outcome).toBe('WIN')
      expect(state.loseReason).toBe('NONE')
      expect(state.elapsedMs).toBe(3200)
    })

    it('버그 수정: 내가 거의 동시에(허용 오차 이내) 졌다면 LOSE를 DRAW로 승격시킨다', () => {
      const state = makeInitialStareState(null)
      startStareRound(state, 1000)
      // 내가 2000ms 시점에 먼저 눈을 감아 패배가 확정된다(state.lostAt = 2000).
      updateStareRound(state, 2000, frame(true, 'BOTH_CLOSED'))
      expect(state.outcome).toBe('LOSE')

      // 상대의 "너 이겼다" 소식이 2000 + 허용오차 이내(예: 300ms 후)에 도착하면 무승부로
      // 승격된다 — 네트워크 중계 지연을 감안해 "거의 동시"로 본다.
      resolveOpponentLoss(state, 2000 + 300)

      expect(state.phase).toBe('finished')
      expect(state.outcome).toBe('DRAW')
    })

    it('허용 오차를 넘겨서 온 소식은 무시하고 LOSE를 그대로 유지한다(진짜로 내가 먼저 짐)', () => {
      const state = makeInitialStareState(null)
      startStareRound(state, 1000)
      updateStareRound(state, 2000, frame(true, 'BOTH_CLOSED')) // 내가 먼저 패배(lostAt=2000)

      // 허용 오차(SIMULTANEOUS_LOSE_TOLERANCE_MS)를 한참 넘겨서 뒤늦게 온 소식은 무시한다.
      resolveOpponentLoss(state, 2000 + SIMULTANEOUS_LOSE_TOLERANCE_MS + 500)

      expect(state.outcome).toBe('LOSE')
      expect(state.elapsedMs).toBe(1000)
    })
  })
})
