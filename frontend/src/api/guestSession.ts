/**
 * 게스트 세션 단독 발급.
 *
 * 대기방 생성·참가와 랜덤 매칭은 응답에 `guestSessionId`를 끼워 주지만, 솔로·AI 모드는 그 어떤
 * 백엔드 호출도 하지 않아서 게스트에게 신원이 생기지 않았다. 이 엔드포인트가 그 경로를 메운다.
 *
 * 이미 유효한 세션을 갖고 있으면 백엔드가 그대로 돌려준다(`apiRequest`가 저장된 세션을
 * `X-Guest-Session-Id` 헤더로 자동으로 붙인다). 그래서 여러 번 불러도 세션이 늘어나지 않는다.
 */
import { apiRequest, storeGuestSessionId } from './http'

export interface GuestSessionResponse {
  guestSessionId: string
  nickname: string
  expiresAt: string
}

export async function issueGuestSession(): Promise<GuestSessionResponse> {
  const data = await apiRequest<GuestSessionResponse>('/guests/session', {
    method: 'POST',
  })
  storeGuestSessionId(data.guestSessionId)
  return data
}
