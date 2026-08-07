import { describe, expect, it } from 'vitest'
import type { CombinedEyeState } from '../eye-tracking/eye-engine'
import {
  canStartStareRound,
  FACE_LOST_LOSE_MS,
  formatDuration,
  getStateWarning,
  makeInitialStareState,
  resetStareRound,
  resolveOpponentLoss,
  SIMULTANEOUS_LOSE_TOLERANCE_MS,
  STARE_AI_DURATIONS_MS,
  startStareRound,
  UNCLEAR_EYE_STATE_LOSE_MS,
  updateStareRound,
} from './stare-core'

function frame(faceDetected: boolean, combinedState: CombinedEyeState) {
  return { faceDetected, combinedState }
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

  it('여전히 BOTH_CLOSED로도 즉시 패배한다', () => {
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

  describe('버그 수정: 한쪽 눈 가리기 편법 (BOTH_OPEN이 아니면 전부 유예 타이머 대상)', () => {
    it('BOTH_HALF_CLOSED가 2초 넘게 지속되면 패배로 종료한다(예전엔 경고만 주고 안 끝났음)', () => {
      const state = makeInitialStareState()
      startStareRound(state, 0)

      // 10ms 시점에 처음 애매한 상태가 시작된다(unclearStateStartedAt = 10).
      expect(updateStareRound(state, 10, frame(true, 'BOTH_HALF_CLOSED'))).toBe(
        'NONE',
      )

      // 2초가 되기 전까지는 경고만 주고 계속 진행되어야 한다(순간적 흔들림 오탐 방지).
      expect(
        updateStareRound(
          state,
          10 + UNCLEAR_EYE_STATE_LOSE_MS - 100,
          frame(true, 'BOTH_HALF_CLOSED'),
        ),
      ).toBe('NONE')
      expect(state.phase).toBe('running')
      expect(state.warning).toContain('경고: 눈이 작게 감김')

      // 2초를 채우면 패배로 종료된다.
      expect(
        updateStareRound(
          state,
          10 + UNCLEAR_EYE_STATE_LOSE_MS + 50,
          frame(true, 'BOTH_HALF_CLOSED'),
        ),
      ).toBe('UNCLEAR_EYE_STATE')
      expect(state.phase).toBe('finished')
      expect(state.loseReason).toBe('UNCLEAR_EYE_STATE')
      expect(state.outcome).toBe('LOSE')
    })

    it('한쪽 눈 가리기로 combinedState가 UNKNOWN이 되는 경우도 2초 후 패배 처리한다', () => {
      // 실제 카메라 테스트에서 확인된 문제: 한쪽 눈을 손으로 가려도 MediaPipe가 그 눈을
      // NOT_DETECTED가 아니라 애매하게 OPEN/HALF_CLOSED로 잘못 추정해서, combineEyeStates가
      // "한쪽만 애매한" 조합을 UNKNOWN으로 뭉뚱그린다 — 특정 상태를 콕 집어 체크하는 대신
      // "BOTH_OPEN이 아니면 전부"로 잡아야 이런 경우도 놓치지 않는다.
      const state = makeInitialStareState()
      startStareRound(state, 0)

      updateStareRound(state, 10, frame(true, 'UNKNOWN'))
      updateStareRound(
        state,
        10 + UNCLEAR_EYE_STATE_LOSE_MS - 100,
        frame(true, 'UNKNOWN'),
      )
      expect(state.phase).toBe('running')

      expect(
        updateStareRound(
          state,
          10 + UNCLEAR_EYE_STATE_LOSE_MS + 50,
          frame(true, 'UNKNOWN'),
        ),
      ).toBe('UNCLEAR_EYE_STATE')
      expect(state.loseReason).toBe('UNCLEAR_EYE_STATE')
    })

    it('BOTH_OPEN으로 돌아오면 타이머가 리셋된다', () => {
      const state = makeInitialStareState()
      startStareRound(state, 0)

      updateStareRound(state, 10, frame(true, 'BOTH_HALF_CLOSED'))
      expect(state.unclearStateStartedAt).not.toBe(0)

      // 명확히 양쪽 다 뜬 상태로 돌아오면 즉시 리셋된다 — 정상적으로 눈을 뜨고 있는데도
      // 패배 타이머가 남아 있으면 오탐이 된다.
      updateStareRound(state, 500, frame(true, 'BOTH_OPEN'))
      expect(state.unclearStateStartedAt).toBe(0)
      expect(state.phase).toBe('running')

      // 리셋된 뒤 새로 애매해진 시점부터 다시 2초를 채워야 한다 — 리셋 전 경과 시간과
      // 합산되지 않는다.
      expect(
        updateStareRound(state, 600, frame(true, 'BOTH_HALF_CLOSED')),
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
