import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentParticipantKey, ensureIdentity } from './identity'

const ACCESS_TOKEN_KEY = 'eye-dont-care.accessToken'
const GUEST_STORAGE_KEY = 'eye-dont-care.guestSessionId'
const ISSUED_GUEST_ID = '27868019-1a91-40d3-8536-a0e5dcf7e8cf'

/** sub=7인 최소 JWT. 서명은 검증하지 않으므로 payload만 맞으면 된다. */
function accessToken(): string {
  const payload = globalThis.btoa(JSON.stringify({ sub: '7' }))
  return `header.${payload}.signature`
}

function guestSessionResponse() {
  return {
    ok: true,
    status: 201,
    json: async () => ({
      code: 'GUEST_SESSION_READY',
      message: '게스트 세션이 준비되었습니다.',
      data: {
        guestSessionId: ISSUED_GUEST_ID,
        nickname: '용감한수달',
        expiresAt: '2026-08-09T00:00:00Z',
      },
    }),
  }
}

type GuestSessionFetch = (
  input: RequestInfo | URL,
  init?: RequestInit,
) => Promise<ReturnType<typeof guestSessionResponse>>

/**
 * 게스트 세션을 돌려주는 fetch 목.
 *
 * 목의 타입을 fetch와 같은 시그니처로 지정해야 mock.calls의 원소 타입이 빈 튜플이
 * 아니어서 호출 인자를 검증할 수 있다.
 */
function guestSessionFetchMock() {
  return vi.fn<GuestSessionFetch>(async () => guestSessionResponse())
}

describe('ensureIdentity', () => {
  beforeEach(() => {
    globalThis.localStorage.clear()
    globalThis.sessionStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    globalThis.localStorage.clear()
    globalThis.sessionStorage.clear()
  })

  it('신원이 없으면 게스트 세션을 발급받아 저장한다', async () => {
    const fetchMock = guestSessionFetchMock()
    vi.stubGlobal('fetch', fetchMock)

    await expect(ensureIdentity()).resolves.toBe(`GUEST:${ISSUED_GUEST_ID}`)

    expect(globalThis.sessionStorage.getItem(GUEST_STORAGE_KEY)).toBe(
      ISSUED_GUEST_ID,
    )
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/guests/session')
  })

  it('회원 토큰이 있으면 발급을 요청하지 않는다', async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken())
    const fetchMock = guestSessionFetchMock()
    vi.stubGlobal('fetch', fetchMock)

    await expect(ensureIdentity()).resolves.toBe('USER:7')

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('게스트 세션이 이미 있으면 발급을 요청하지 않는다', async () => {
    globalThis.sessionStorage.setItem(GUEST_STORAGE_KEY, 'guest-1')
    const fetchMock = guestSessionFetchMock()
    vi.stubGlobal('fetch', fetchMock)

    await expect(ensureIdentity()).resolves.toBe('GUEST:guest-1')

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('동시에 불러도 발급은 한 번만 요청한다', async () => {
    const fetchMock = guestSessionFetchMock()
    vi.stubGlobal('fetch', fetchMock)

    const results = await Promise.all([
      ensureIdentity(),
      ensureIdentity(),
      ensureIdentity(),
    ])

    expect(results).toEqual(Array(3).fill(`GUEST:${ISSUED_GUEST_ID}`))
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  // 신원 확보는 화면을 막을 일이 아니다. 실패해도 앱은 그대로 동작해야 한다.
  it('발급에 실패하면 예외 대신 null을 돌려준다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: false,
        status: 429,
        json: async () => ({
          code: 'GUEST-003',
          message: '게스트 세션 발급 요청이 너무 많습니다.',
          data: null,
        }),
      })),
    )

    await expect(ensureIdentity()).resolves.toBeNull()

    expect(globalThis.sessionStorage.getItem(GUEST_STORAGE_KEY)).toBeNull()
    expect(currentParticipantKey()).toBeNull()
  })

  it('실패한 뒤 다시 부르면 재시도한다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 503,
        json: async () => ({ code: 'GUEST-002', message: '', data: null }),
      })
      .mockResolvedValueOnce(guestSessionResponse())
    vi.stubGlobal('fetch', fetchMock)

    await expect(ensureIdentity()).resolves.toBeNull()
    await expect(ensureIdentity()).resolves.toBe(`GUEST:${ISSUED_GUEST_ID}`)

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
