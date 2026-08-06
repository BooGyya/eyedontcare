import { describe, expect, it } from 'vitest'
import {
  applyRhythmInput,
  DEFAULT_RHYTHM_DURATION_MS,
  DEFAULT_RHYTHM_HEALTH,
  finishRhythmRound,
  getRhythmAccuracy,
  HIT_WINDOWS,
  judgeRhythmDelta,
  makeInitialRhythmState,
  scoreRhythmJudgement,
  startRhythmRound,
  updateRhythmRound,
} from './rhythm-core'

describe('rhythm-core', () => {
  it('기본값이 기획 확정본(30초, 하트 5개)과 일치한다', () => {
    const state = makeInitialRhythmState()
    expect(state.durationMs).toBe(DEFAULT_RHYTHM_DURATION_MS)
    expect(state.durationMs).toBe(30000)
    expect(state.maxHealth).toBe(DEFAULT_RHYTHM_HEALTH)
    expect(state.maxHealth).toBe(5)
    expect(state.phase).toBe('ready')
  })

  it('노트 이동 시간 기본값이 2800ms로 느려졌다', () => {
    expect(makeInitialRhythmState().noteTravelMs).toBe(2800)
  })

  it('랜덤 생성 모드는 startDelayMs만큼 첫 노트 등장을 늦춘다', () => {
    const withDelay = makeInitialRhythmState()
    startRhythmRound(withDelay, 0, { startDelayMs: 2000 })
    const noDelay = makeInitialRhythmState()
    startRhythmRound(noDelay, 0, { startDelayMs: 0 })
    expect(withDelay.nextBeatAt - noDelay.nextBeatAt).toBe(2000)
  })

  it('startDelayMs는 비트맵(음악) 모드의 노트 타이밍에 영향을 주지 않는다', () => {
    const state = makeInitialRhythmState({
      beatmapEntries: [{ timeMs: 1000, lanes: ['LEFT_EYE'] }],
    })
    startRhythmRound(state, 0, {
      beatmapEntries: [{ timeMs: 1000, lanes: ['LEFT_EYE'] }],
      startDelayMs: 2000,
    })
    updateRhythmRound(state, 0)
    expect(state.notes[0].hitAt).toBe(1000)
  })

  it('시간차에 따라 PERFECT/GREAT/GOOD/MISS를 판정한다', () => {
    expect(judgeRhythmDelta(0)).toBe('PERFECT')
    expect(judgeRhythmDelta(90)).toBe('PERFECT')
    expect(judgeRhythmDelta(150)).toBe('GREAT')
    expect(judgeRhythmDelta(250)).toBe('GOOD')
    expect(judgeRhythmDelta(400)).toBe('MISS')
  })

  it('콤보 10 이상 1.5배, 20 이상 2배 보너스가 붙는다', () => {
    expect(scoreRhythmJudgement('PERFECT', 5)).toBe(100)
    expect(scoreRhythmJudgement('PERFECT', 10)).toBe(150)
    expect(scoreRhythmJudgement('PERFECT', 20)).toBe(200)
    expect(scoreRhythmJudgement('MISS', 20)).toBe(0)
  })

  it('고정 비트맵으로 노트를 생성하고 정확한 타이밍에 맞추면 PERFECT로 판정한다', () => {
    const state = makeInitialRhythmState({
      beatmapEntries: [{ timeMs: 1000, lanes: ['LEFT_EYE'] }],
    })
    startRhythmRound(state, 0)
    updateRhythmRound(state, 0) // 비트맵 노트를 생성

    expect(state.notes).toHaveLength(1)
    expect(state.notes[0].hitAt).toBe(1000)

    const result = applyRhythmInput(state, 'LEFT_EYE', 1000)
    expect(result.hit).toBe(true)
    expect(result.judgement).toBe('PERFECT')
    expect(state.score).toBe(100)
    expect(state.combo).toBe(1)
    expect(state.hits).toBe(1)
  })

  it('타이밍을 놓치면 MISS 처리되고 체력이 깎이며 콤보가 끊긴다', () => {
    const state = makeInitialRhythmState({
      beatmapEntries: [{ timeMs: 1000, lanes: ['LEFT_EYE'] }],
      health: 3,
    })
    startRhythmRound(state, 0, {
      health: 3,
      beatmapEntries: [{ timeMs: 1000, lanes: ['LEFT_EYE'] }],
    })
    updateRhythmRound(state, 0)
    state.combo = 4 // 이전 콤보가 있었다고 가정

    // 히트 윈도우(GOOD)를 한참 지나서 업데이트하면 MISS 처리된다.
    updateRhythmRound(state, 1000 + HIT_WINDOWS.GOOD + 50)
    expect(state.misses).toBe(1)
    expect(state.health).toBe(2)
    expect(state.combo).toBe(0)
  })

  it('체력이 0이 되면 즉시 종료(HEALTH_EMPTY)된다', () => {
    const state = makeInitialRhythmState({ health: 1 })
    startRhythmRound(state, 0, { health: 1 })
    state.health = 0

    expect(updateRhythmRound(state, 100)).toBe('FINISHED')
    expect(state.phase).toBe('finished')
    expect(state.finishReason).toBe('HEALTH_EMPTY')
  })

  it('제한 시간이 끝나면 TIME_UP으로 종료된다', () => {
    // health를 충분히 높게 잡아 "5초간 자동 생성된 노트를 못 맞춰 체력 소진"이 아니라
    // 정말로 "시간 종료"가 원인임을 검증한다.
    const state = makeInitialRhythmState({ durationMs: 5000, health: 50 })
    startRhythmRound(state, 0, { durationMs: 5000, health: 50 })

    expect(updateRhythmRound(state, 5000)).toBe('FINISHED')
    expect(state.finishReason).toBe('TIME_UP')
  })

  it('정확도는 히트/미스 비율로 계산된다', () => {
    const state = makeInitialRhythmState()
    state.hits = 8
    state.misses = 2
    expect(getRhythmAccuracy(state)).toBe(80)
  })

  it('finishRhythmRound은 이미 끝난 라운드를 다시 끝내지 않는다', () => {
    const state = makeInitialRhythmState()
    startRhythmRound(state, 0)
    finishRhythmRound(state, 100, 'MANUAL')
    const messageAfterFirstFinish = state.message
    finishRhythmRound(state, 200, 'TIME_UP')
    expect(state.message).toBe(messageAfterFirstFinish)
    expect(state.finishReason).toBe('MANUAL')
  })
})
