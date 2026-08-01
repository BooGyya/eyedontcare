/**
 * 대기방·매칭 WebSocket에 쓸 신원(identity)을 한곳에서 만든다.
 *
 * 회원은 저장된 access 토큰(JWT), 게스트는 sessionStorage의 `guestSessionId`를 쓴다.
 * 토큰은 localStorage(단일 출처, 재발급 시 갱신됨)에서 직접 읽어 항상 최신 값을 반영한다.
 */
import { getAccessToken } from './authTokens'
import { decodeUserId } from './jwt'
import { getStoredGuestSessionId } from './http'
import type { WaitingRoomIdentity } from '../types/waitingRoom'

/**
 * 현재 사용자의 신원을 만든다. 회원 토큰이 있으면 우선하고, 없으면 게스트 세션을 쓴다.
 * 둘 다 없으면(세션 미발급 신규 게스트) `null` — 호출부가 먼저 세션을 확보해야 한다.
 */
export function resolveIdentity(): WaitingRoomIdentity | null {
  const accessToken = getAccessToken()
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
  return getAccessToken()
}

/**
 * 게임 결과 제출 등에 쓸 참가자 키. 백엔드가 요청 신원으로 계산하는 값과 같아야 한다.
 * 회원 `USER:{userId}`(토큰 sub에서), 게스트 `GUEST:{guestSessionId}`. 둘 다 없으면 null.
 */
export function currentParticipantKey(): string | null {
  const token = getAccessToken()
  if (token) {
    const userId = decodeUserId(token)
    if (userId !== null) return `USER:${userId}`
  }
  const guestSessionId = getStoredGuestSessionId()
  if (guestSessionId) return `GUEST:${guestSessionId}`
  return null
}

/** 현재 참가자 유형. 회원 'USER', 게스트 'GUEST', 신원 없으면 null. */
export function currentParticipantType(): 'USER' | 'GUEST' | null {
  if (getAccessToken()) return 'USER'
  if (getStoredGuestSessionId()) return 'GUEST'
  return null
}
