import { describe, expect, it } from 'vitest'
import {
  applyBlinkEvent,
  BLINK_GAME_SECONDS,
  BLINK_SCORE_UNIT,
  formatRemainingTime,
  getBlinkRatePerSecond,
  isCountableBlinkEvent,
  makeInitialBlinkState,
  resetBlinkRound,
  startBlinkRound,
  updateBlinkTimer,
} from './blink-core'

describe('blink-core', () => {
  it('starts with a 20-second countdown matching the finalized spec', () => {
    const state = makeInitialBlinkState()
    expect(state.durationMs).toBe(BLINK_GAME_SECONDS * 1000)
    expect(state.phase).toBe('ready')
    expect(formatRemainingTime(state.remainingMs)).toBe('00:20')
  })

  it('counts BLINK/FAST_BLINK/DOUBLE_BLINK toward the score, but not LONG_CLOSE/WINK', () => {
    const state = makeInitialBlinkState()
    startBlinkRound(state, 1000)
    expect(state.phase).toBe('running')
    expect(state.remainingMs).toBe(20000)

    expect(applyBlinkEvent(state, { type: 'BLINK' })).toBe(true)
    expect(applyBlinkEvent(state, { type: 'FAST_BLINK' })).toBe(true)
    expect(applyBlinkEvent(state, { type: 'DOUBLE_BLINK' })).toBe(true)
    expect(state.blinkCount).toBe(3)
    expect(state.fastBlinkCount).toBe(1)
    expect(state.doubleBlinkCount).toBe(1)
    expect(state.score).toBe(3 * BLINK_SCORE_UNIT)

    expect(applyBlinkEvent(state, { type: 'LONG_CLOSE' })).toBe(false)
    expect(state.longCloseCount).toBe(1)
    expect(applyBlinkEvent(state, { type: 'LEFT_WINK' })).toBe(false)
    expect(state.winkCount).toBe(1)
  })

  it('stops counting once the timer runs out', () => {
    const state = makeInitialBlinkState()
    startBlinkRound(state, 1000)
    applyBlinkEvent(state, { type: 'BLINK' })
    applyBlinkEvent(state, { type: 'FAST_BLINK' })
    applyBlinkEvent(state, { type: 'DOUBLE_BLINK' })

    updateBlinkTimer(state, 11000)
    expect(formatRemainingTime(state.remainingMs)).toBe('00:10')
    expect(getBlinkRatePerSecond(state).toFixed(2)).toBe('0.30')

    expect(updateBlinkTimer(state, 21000)).toBe(true)
    expect(state.phase).toBe('finished')
    expect(applyBlinkEvent(state, { type: 'BLINK' })).toBe(false)
    expect(state.blinkCount).toBe(3)
  })

  it('classifies which event types count toward the score', () => {
    expect(isCountableBlinkEvent('BLINK')).toBe(true)
    expect(isCountableBlinkEvent('FAST_BLINK')).toBe(true)
    expect(isCountableBlinkEvent('DOUBLE_BLINK')).toBe(true)
    expect(isCountableBlinkEvent('LONG_CLOSE')).toBe(false)
  })

  it('resets back to a fresh ready state', () => {
    const state = makeInitialBlinkState()
    startBlinkRound(state, 1000)
    applyBlinkEvent(state, { type: 'BLINK' })

    resetBlinkRound(state)
    expect(state.phase).toBe('ready')
    expect(state.blinkCount).toBe(0)
    expect(state.remainingMs).toBe(20000)
  })
})
