/**
 * 대기방·매칭 WebSocket에 쓸 신원(identity)을 한곳에서 만든다.
 *
 * 로그인 회원은 `accessToken`(JWT), 게스트는 sessionStorage에 저장된 `guestSessionId`를 쓴다.
 * 아직 Auth가 연동되지 않아 실제로는 게스트 경로로 동작하지만, 로그인이 붙으면
 * `authStore.accessToken`이 채워져 별도 수정 없이 회원 경로가 켜진다.
 */
import { useAuthStore } from '../stores/auth'
import { getStoredGuestSessionId } from './http'
import type { WaitingRoomIdentity } from '../types/waitingRoom'

/**
 * 현재 사용자의 신원을 만든다. 회원 토큰이 있으면 우선하고, 없으면 게스트 세션을 쓴다.
 * 둘 다 없으면(세션 미발급 신규 게스트) `null` — 호출부가 먼저 세션을 확보해야 한다.
 */
export function resolveIdentity(): WaitingRoomIdentity | null {
  const accessToken = useAuthStore().accessToken
  if (accessToken) {
    return { accessToken }
  }
  const guestSessionId = getStoredGuestSessionId()
  if (guestSessionId) {
    return { guestSessionId }
  }
  return null
}

/** REST 호출에 넘길 회원 토큰. 없으면 `null`(게스트 헤더로 대체됨). */
export function currentAccessToken(): string | null {
  return useAuthStore().accessToken
}
