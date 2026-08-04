import { describe, expect, it } from 'vitest'
import type { CombinedEyeState } from '../eye-tracking/eye-engine'
import {
  canStartStareRound,
  FACE_LOST_LOSE_MS,
  finishStareRoundAsWinner,
  formatDuration,
  getStateWarning,
  makeInitialStareState,
  resetStareRound,
  STARE_AI_DURATIONS_MS,
  startStareRound,
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

  it('finishStareRoundAsWinner: 상대가 먼저 패배하면 승리로 종료한다', () => {
    const state = makeInitialStareState(null)
    startStareRound(state, 1000)
    updateStareRound(state, 4000, frame(true, 'BOTH_OPEN'))

    finishStareRoundAsWinner(state, 4200)

    expect(state.phase).toBe('finished')
    expect(state.outcome).toBe('WIN')
    expect(state.loseReason).toBe('NONE')
    expect(state.elapsedMs).toBe(3200)
  })

  it('finishStareRoundAsWinner: 이미 끝난 라운드는 다시 건드리지 않는다', () => {
    const state = makeInitialStareState(null)
    startStareRound(state, 1000)
    updateStareRound(state, 2000, frame(true, 'BOTH_CLOSED')) // 내가 먼저 패배

    finishStareRoundAsWinner(state, 3000) // 상대 패배 알림이 뒤늦게 와도 무시돼야 함

    expect(state.outcome).toBe('LOSE')
    expect(state.elapsedMs).toBe(1000)
  })
})
