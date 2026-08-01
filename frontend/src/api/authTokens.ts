/**
 * 회원 인증 토큰 저장소.
 *
 * access/refresh 토큰을 localStorage에 보관해 새로고침·탭 간에 로그인을 유지한다.
 * 게스트 세션(sessionStorage)과 달리 회원은 브라우저 전체에서 하나의 신원이므로 localStorage를 쓴다.
 * `api/http.ts`(Bearer 부착·401 재발급)와 스토어(로그인/로그아웃)가 이 모듈을 단일 출처로 공유한다.
 */
import type { TokenResponse } from '../types/auth'

const ACCESS_TOKEN_KEY = 'eye-dont-care.accessToken'
const REFRESH_TOKEN_KEY = 'eye-dont-care.refreshToken'

function storage(): globalThis.Storage | null {
  return globalThis.localStorage ?? null
}

export function getAccessToken(): string | null {
  return storage()?.getItem(ACCESS_TOKEN_KEY) ?? null
}

export function getRefreshToken(): string | null {
  return storage()?.getItem(REFRESH_TOKEN_KEY) ?? null
}

export function setTokens(tokens: TokenResponse): void {
  const store = storage()
  if (!store) return
  store.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  store.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
}

export function clearTokens(): void {
  const store = storage()
  if (!store) return
  store.removeItem(ACCESS_TOKEN_KEY)
  store.removeItem(REFRESH_TOKEN_KEY)
}

/**
 * 세션 만료(자동 재발급 실패) 시 호출할 콜백. 스토어가 등록해 게스트 상태로 전환한다.
 * `http.ts`가 스토어를 직접 import하지 않도록(순환참조 회피) 콜백 등록 방식을 쓴다.
 */
let sessionExpiredHandler: (() => void) | null = null

export function setSessionExpiredHandler(handler: (() => void) | null): void {
  sessionExpiredHandler = handler
}

export function notifySessionExpired(): void {
  sessionExpiredHandler?.()
}
