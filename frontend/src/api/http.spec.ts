import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './http'

const ACCESS_TOKEN_KEY = 'eye-dont-care.accessToken'
const REFRESH_TOKEN_KEY = 'eye-dont-care.refreshToken'

interface FetchInit {
  method?: string
  headers?: Record<string, string>
  body?: string
}

describe('apiRequest 토큰 재발급 single-flight', () => {
  beforeEach(() => {
    globalThis.localStorage.clear()
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, 'old-access')
    globalThis.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-1')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    globalThis.localStorage.clear()
  })

  it('동시 401은 재발급을 한 번만 호출하고 두 요청 모두 성공한다', async () => {
    let reissueCalls = 0

    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: FetchInit) => {
        if (url.endsWith('/auth/reissue')) {
          reissueCalls += 1
          // 재발급은 토큰을 회전한다(새 access/refresh 발급).
          return {
            ok: true,
            status: 200,
            json: async () => ({
              code: 'OK',
              message: '',
              data: { accessToken: 'new-access', refreshToken: 'refresh-2' },
            }),
          }
        }
        // 보호 자원: 새 access 토큰이어야 200, 옛 토큰이면 401.
        const authorized = init?.headers?.Authorization === 'Bearer new-access'
        return {
          ok: authorized,
          status: authorized ? 200 : 401,
          json: async () =>
            authorized
              ? { code: 'OK', message: '', data: { value: 42 } }
              : { code: 'SECURITY-001', message: '인증이 필요합니다.', data: null },
        }
      }),
    )

    const [a, b] = await Promise.all([
      apiRequest('/protected/a'),
      apiRequest('/protected/b'),
    ])

    // 동시 401이 각자 재발급을 부르면 refresh 토큰 회전 레이스로 로그아웃된다.
    // single-flight로 재발급은 한 번만 일어나고 두 요청 모두 새 토큰으로 성공해야 한다.
    expect(reissueCalls).toBe(1)
    expect(a).toEqual({ value: 42 })
    expect(b).toEqual({ value: 42 })
    expect(globalThis.localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('new-access')
  })
})
