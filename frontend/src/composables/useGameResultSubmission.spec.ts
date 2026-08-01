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
    { gameId: 1, gameName: 'HOCKEY', playMode: 'INVITE' },
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
            data: postOk ? { resultId: 1 } : null,
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

    await useGameResultSubmission().submitPlayedResult({
      gameSlug: 'hold',
      mode: 'solo',
      startedAt: '2026-08-01T10:00:00.000Z',
      score: 120,
    })

    const post = calls.find((c) => c.url.includes('/game-results'))
    expect(post).toBeTruthy()
    const body = post?.body as {
      gameId: number
      participants: { participantKey: string; participantType: string; outcome: string }[]
    }
    expect(body.gameId).toBe(4) // EYEFIGHT + SOLO
    expect(body.participants[0].participantKey).toBe('USER:8')
    expect(body.participants[0].participantType).toBe('USER')
    expect(body.participants[0].outcome).toBe('COMPLETED') // solo
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
      participants: { participantKey: string; participantType: string; outcome: string }[]
    }
    expect(body.participants[0].participantKey).toBe('GUEST:guest-abc')
    expect(body.participants[0].participantType).toBe('GUEST')
    expect(body.participants[0].outcome).toBe('WIN') // non-solo, non-draw
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
    ).resolves.toBeUndefined()
  })
})
