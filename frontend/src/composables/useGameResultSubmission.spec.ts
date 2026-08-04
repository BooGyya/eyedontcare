import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useGameResultSubmission } from './useGameResultSubmission'
import { resetGamesCache } from '../api/game'

const ACCESS_TOKEN_KEY = 'eye-dont-care.accessToken'
const GUEST_KEY = 'eye-dont-care.guestSessionId'

const CATALOG = {
  games: [
    { gameId: 4, gameName: 'EYEFIGHT', playMode: 'SOLO' },
    { gameId: 5, gameName: 'EYEFIGHT', playMode: 'INVITE' },
    { gameId: 6, gameName: 'BLINK', playMode: 'SOLO' },
    { gameId: 7, gameName: 'RHYTHM', playMode: 'SOLO' },
    { gameId: 1, gameName: 'HOCKEY', playMode: 'INVITE' },
    { gameId: 2, gameName: 'HOCKEY', playMode: 'AI' },
  ],
}

function base64url(value: unknown): string {
  return globalThis
    .btoa(JSON.stringify(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

function fakeAccessToken(userId: number): string {
  return `header.${base64url({ sub: userId })}.sig`
}

interface FetchCall {
  url: string
  method: string
  body: unknown
}

function stubFetch({ postOk = true } = {}): FetchCall[] {
  const calls: FetchCall[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: { method?: string; body?: string }) => {
      calls.push({
        url,
        method: init?.method ?? 'GET',
        body: init?.body ? JSON.parse(init.body) : undefined,
      })
      if (url.includes('/game-results')) {
        return {
          ok: postOk,
          status: postOk ? 201 : 400,
          json: async () => ({
            code: postOk ? 'OK' : 'ERR',
            message: '',
            data: postOk
              ? { resultId: 1, isNewRecord: true, previousBestScore: null }
              : null,
          }),
        }
      }
      // /games
      return {
        ok: true,
        status: 200,
        json: async () => ({ code: 'OK', message: '', data: CATALOG }),
      }
    }),
  )
  return calls
}

describe('useGameResultSubmission', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resetGamesCache()
    globalThis.localStorage?.clear()
    globalThis.sessionStorage?.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('submits a member result with the USER participant key', async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, fakeAccessToken(8))
    const calls = stubFetch()

    const submitted = await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'hold',
      mode: 'solo',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 120,
    })

    expect(submitted).toEqual({
      resultId: 1,
      isNewRecord: true,
      previousBestScore: null,
    })

    const post = calls.find((c) => c.url.includes('/game-results'))
    expect(post).toBeTruthy()
    const body = post?.body as {
      gameId: number
      participants: {
        participantKey: string
        participantType: string
        outcome: string
      }[]
      gameResult: Record<string, unknown>
    }
    expect(body.gameId).toBe(4) // EYEFIGHT + SOLO
    expect(body.participants[0].participantKey).toBe('USER:8')
    expect(body.participants[0].participantType).toBe('USER')
    expect(body.participants[0].outcome).toBe('COMPLETED') // solo
    expect(body.gameResult).toEqual({ '1': { score: 120 } })
  })

  it('adds game-specific result data without changing the score path', async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, fakeAccessToken(8))
    const calls = stubFetch()

    await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'hold',
      mode: 'solo',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 28,
      resultData: { survivalTimeMs: 28_400 },
    })

    const post = calls.find((c) => c.url.includes('/game-results'))
    const body = post?.body as { gameResult: Record<string, unknown> }
    expect(body.gameResult).toEqual({
      '1': { score: 28, survivalTimeMs: 28_400 },
    })
  })

  it('stores the blink count alongside the blink ranking score', async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, fakeAccessToken(8))
    const calls = stubFetch()

    await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'blink',
      mode: 'solo',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 360,
      resultData: { blinkCount: 36 },
    })

    const post = calls.find((c) => c.url.includes('/game-results'))
    const body = post?.body as { gameResult: Record<string, unknown> }
    expect(body.gameResult).toEqual({
      '1': { score: 360, blinkCount: 36 },
    })
  })

  it('stores rhythm combo and remaining hearts alongside the score', async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, fakeAccessToken(8))
    const calls = stubFetch()

    await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'rhythm',
      mode: 'solo',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 1_860,
      resultData: { maxCombo: 24, remainingHearts: 3 },
    })

    const post = calls.find((c) => c.url.includes('/game-results'))
    const body = post?.body as { gameResult: Record<string, unknown> }
    expect(body.gameResult).toEqual({
      '1': { score: 1_860, maxCombo: 24, remainingHearts: 3 },
    })
  })

  it('stores the AI score with the air hockey result', async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, fakeAccessToken(8))
    const calls = stubFetch()

    await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'air',
      mode: 'ai',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 5,
      outcome: 'WIN',
      resultData: { opponentScore: 3 },
    })

    const post = calls.find((c) => c.url.includes('/game-results'))
    const body = post?.body as {
      gameId: number
      participants: { outcome: string }[]
      gameResult: Record<string, unknown>
    }
    expect(body.gameId).toBe(2) // HOCKEY + AI
    expect(body.participants[0].outcome).toBe('WIN')
    expect(body.gameResult).toEqual({ '1': { score: 5, opponentScore: 3 } })
  })

  it('skips submission when no game id maps to the slot', async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, fakeAccessToken(8))
    const calls = stubFetch()

    // air + solo -> HOCKEY + SOLO, which is not in the catalog.
    await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'air',
      mode: 'solo',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 0,
    })

    expect(calls.some((c) => c.url.includes('/game-results'))).toBe(false)
  })

  it('skips submission when there is no identity', async () => {
    const calls = stubFetch()

    await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'hold',
      mode: 'solo',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 0,
    })

    expect(calls).toHaveLength(0)
  })

  it('uses the guest participant key when only a guest session exists', async () => {
    globalThis.sessionStorage.setItem(GUEST_KEY, 'guest-abc')
    const calls = stubFetch()

    await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'hold',
      mode: 'friends',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 3,
    })

    const post = calls.find((c) => c.url.includes('/game-results'))
    const body = post?.body as {
      participants: {
        participantKey: string
        participantType: string
        outcome: string
      }[]
    }
    expect(body.participants[0].participantKey).toBe('GUEST:guest-abc')
    expect(body.participants[0].participantType).toBe('GUEST')
    expect(body.participants[0].outcome).toBe('COMPLETED') // 승패를 확정하지 못한 경우
  })

  it('does not throw when submission fails', async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, fakeAccessToken(8))
    stubFetch({ postOk: false })

    await expect(
      useGameResultSubmission().submitPlayedResult({
        gameSlug: 'hold',
        mode: 'solo',
        startedAt: '2026-08-01T10:00:00.000Z',
        score: 1,
      }),
    ).resolves.toBeNull()
  })
})
