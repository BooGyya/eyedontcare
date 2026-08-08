import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearPlayEntry,
  consumePlayEntry,
  consumeSoloPlayEntry,
  issuePlayEntry,
  issueSoloPlayEntry,
  SOLO_PLAY_ENTRY_KEY,
  SOLO_PLAY_ENTRY_TTL_MS,
  type PlayEntryMode,
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

  it.each<PlayEntryMode>(['solo', 'ai'])(
    'issues a %s ticket for the current participant',
    (mode) => {
      expect(issuePlayEntry('blink', mode)).toBe(true)

      expect(
        JSON.parse(
          globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY) ?? '',
        ),
      ).toEqual({
        gameId: 'blink',
        mode,
        participantKey: 'GUEST:guest-1',
        issuedAt: expect.any(Number),
      })
    },
  )

  it.each<PlayEntryMode>(['solo', 'ai'])(
    'consumes a valid %s ticket once',
    (mode) => {
      issuePlayEntry('blink', mode)

      expect(consumePlayEntry('blink', mode)).toBe(true)
      expect(consumePlayEntry('blink', mode)).toBe(false)
      expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
    },
  )

  it('keeps the existing SOLO wrapper behavior', () => {
    expect(issueSoloPlayEntry('blink')).toBe(true)
    expect(consumeSoloPlayEntry('blink')).toBe(true)
  })

  it.each([
    ['SOLO ticket in AI mode', 'solo', 'ai'],
    ['AI ticket in SOLO mode', 'ai', 'solo'],
  ] as const)(
    'rejects %s and removes it',
    (_reason, issuedMode, consumedMode) => {
      issuePlayEntry('blink', issuedMode)

      expect(consumePlayEntry('blink', consumedMode)).toBe(false)
      expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
    },
  )

  it.each([
    [
      'different game',
      { gameId: 'rhythm', mode: 'solo', participantKey: 'GUEST:guest-1' },
    ],
    [
      'different participant',
      { gameId: 'blink', mode: 'solo', participantKey: 'GUEST:guest-2' },
    ],
    [
      'expired',
      {
        gameId: 'blink',
        mode: 'solo',
        participantKey: 'GUEST:guest-1',
        issuedAt: Date.now() - SOLO_PLAY_ENTRY_TTL_MS - 1,
      },
    ],
  ])('rejects a %s ticket and removes it', (_reason, entry) => {
    globalThis.sessionStorage.setItem(
      SOLO_PLAY_ENTRY_KEY,
      JSON.stringify({ issuedAt: Date.now(), ...entry }),
    )

    expect(consumePlayEntry('blink', 'solo')).toBe(false)
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })

  it.each([
    ['legacy payload without mode', { gameId: 'blink' }],
    ['unsupported mode', { gameId: 'blink', mode: 'friends' }],
  ])('rejects a %s and removes it', (_reason, entry) => {
    globalThis.sessionStorage.setItem(
      SOLO_PLAY_ENTRY_KEY,
      JSON.stringify({
        ...entry,
        participantKey: 'GUEST:guest-1',
        issuedAt: Date.now(),
      }),
    )

    expect(consumePlayEntry('blink', 'solo')).toBe(false)
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })

  it('rejects malformed JSON and removes it', () => {
    globalThis.sessionStorage.setItem(SOLO_PLAY_ENTRY_KEY, '{invalid')

    expect(consumePlayEntry('blink', 'solo')).toBe(false)
    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })

  it('fails closed when sessionStorage cannot be read or written', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    expect(issuePlayEntry('blink', 'ai')).toBe(false)

    vi.restoreAllMocks()
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    expect(consumePlayEntry('blink', 'ai')).toBe(false)
  })

  it('fails closed when a valid ticket cannot be removed', () => {
    issuePlayEntry('blink', 'ai')
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })

    expect(consumePlayEntry('blink', 'ai')).toBe(false)
  })

  it('can clear a stale ticket explicitly', () => {
    issuePlayEntry('blink', 'ai')

    clearPlayEntry()

    expect(globalThis.sessionStorage.getItem(SOLO_PLAY_ENTRY_KEY)).toBeNull()
  })
})
