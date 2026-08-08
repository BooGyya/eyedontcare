/**
 * 대기방·매칭 WebSocket에 쓸 신원(identity)을 한곳에서 만든다.
 *
 * 회원은 저장된 access 토큰(JWT), 게스트는 sessionStorage의 `guestSessionId`를 쓴다.
 * 토큰은 localStorage(단일 출처, 재발급 시 갱신됨)에서 직접 읽어 항상 최신 값을 반영한다.
 */
import { getAccessToken } from './authTokens'
import { decodeUserId } from './jwt'
import { getStoredGuestSessionId } from './http'
import { issueGuestSession } from './guestSession'
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

/** 진행 중인 게스트 세션 발급 하나를 공유한다(single-flight). */
let guestSessionInFlight: Promise<string | null> | null = null

/**
 * 신원이 없으면 게스트 세션을 발급받아 확보한다. 이미 있으면 아무것도 하지 않는다.
 *
 * ## 왜 필요한가
 * 게스트의 `guestSessionId`는 원래 대기방·매칭 응답으로만 내려왔다. 그래서 백엔드를 거치지 않는
 * 솔로·AI 모드로 바로 들어온 비로그인 사용자는 {@link currentParticipantKey}가 `null`이었고,
 * 그 값을 필요로 하는 플레이 입장권 발급(`issuePlayEntry`)과 결과 제출이 모두 조용히 실패했다.
 * 앱이 뜰 때 미리 확보해 두면 신원이 항상 존재하게 되어 두 문제가 함께 사라진다.
 *
 * ## 미리 확보해야 하는 이유
 * 입장권 발급은 카운트다운이 끝나는 순간 **동기로** 일어난다. 그때 가서 발급을 기다릴 수 없으므로
 * 부팅 시점과 준비방 진입 시점처럼 여유 있는 곳에서 앞당겨 호출한다.
 *
 * 실패해도 예외를 던지지 않는다 — 신원 확보는 화면 렌더링을 막을 일이 아니다. 호출부는 결과를
 * 기다리지 않아도 되고, 실패하면 다음 호출에서 다시 시도한다.
 *
 * @returns 확보된 참가자 키. 실패하면 `null`.
 */
export function ensureIdentity(): Promise<string | null> {
  const existing = currentParticipantKey()
  if (existing) return Promise.resolve(existing)
  if (guestSessionInFlight) return guestSessionInFlight

  guestSessionInFlight = requestGuestIdentity().finally(() => {
    guestSessionInFlight = null
  })

  return guestSessionInFlight
}

async function requestGuestIdentity(): Promise<string | null> {
  try {
    await issueGuestSession()
  } catch {
    // 백엔드 장애·요청 제한 등. 앱은 그대로 동작하고 다음 호출에서 다시 시도한다.
    return null
  }

  return currentParticipantKey()
}

/** 현재 참가자 유형. 회원 'USER', 게스트 'GUEST', 신원 없으면 null. */
export function currentParticipantType(): 'USER' | 'GUEST' | null {
  if (getAccessToken()) return 'USER'
  if (getStoredGuestSessionId()) return 'GUEST'
  return null
}
