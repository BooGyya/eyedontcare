import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearSoloPlayEntry,
  consumeSoloPlayEntry,
  issueSoloPlayEntry,
  SOLO_PLAY_ENTRY_KEY,
  SOLO_PLAY_ENTRY_TTL_MS,
} from './soloPlayEntry'

const GUEST_STORAGE_KEY = 'eye-dont-care.guestSessionId'

describe('soloPlayEntry', () => {
  beforeEach(() => {
    globalThis.localStorage.clear()
    globalThis.sessionStorage.clear()
    globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
  })

  afterEach(() => {
    vi.restoreAllMocks()
    globalThis.localStorage.clear()
    globalThis.sessionStorage.clear()
  })

  it('issues a ticket for the current participant', () => {
    expect(issueSoloPlayEntry('blink')).toBe(true)

    expect(
      JSON.parse(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY) ?? ''),
    ).toEqual({
      gameId: 'blink',
      participantKey: 'GUEST:guest-1',
      issuedAt: expect.any(Number),
    })
  })

  it('consumes a valid ticket once', () => {
    issueSoloPlayEntry('blink')

    expect(consumeSoloPlayEntry('blink')).toBe(true)
    expect(consumeSoloPlayEntry('blink')).toBe(false)
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })

  it.each([
    ['different game', { gameId: 'rhythm', participantKey: 'GUEST:guest-1' }],
    [
      'different participant',
      { gameId: 'blink', participantKey: 'GUEST:guest-2' },
    ],
    [
      'expired',
      {
        gameId: 'blink',
        participantKey: 'GUEST:guest-1',
        issuedAt: Date.now() - SOLO_PLAY_ENTRY_TTL_MS - 1,
      },
    ],
  ])('rejects a %s ticket and removes it', (_reason, entry) => {
    globalThis.sessionStorage.setItem(
      SOLO_PLAY_ENTRY_KEY,
      JSON.stringify({ issuedAt: Date.now(), ...entry }),
    )

    expect(consumeSoloPlayEntry('blink')).toBe(false)
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })

  it('rejects malformed JSON and removes it', () => {
    globalThis.sessionStorage.setItem(SOLO_PLAY_ENTRY_KEY, '{invalid')

    expect(consumeSoloPlayEntry('blink')).toBe(false)
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })

  it('fails closed when sessionStorage cannot be read or written', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    expect(issueSoloPlayEntry('blink')).toBe(false)

    vi.restoreAllMocks()
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    expect(consumeSoloPlayEntry('blink')).toBe(false)
  })

  it('fails closed when a valid ticket cannot be removed', () => {
    issueSoloPlayEntry('blink')
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })

    expect(consumeSoloPlayEntry('blink')).toBe(false)
  })

  it('can clear a stale ticket explicitly', () => {
    issueSoloPlayEntry('blink')

    clearSoloPlayEntry()

    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })
})
