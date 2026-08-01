/**
 * 랜덤 매칭 REST 호출.
 *
 * `joinMatch`는 매칭 큐에 신청한다. 게스트가 세션 없이 호출하면 서버가 게스트 세션을 발급해
 * 응답에 담아 주므로, 그 값을 저장해 이후 `/ws/match`·대기방 접속에서 같은 신원을 재사용한다.
 * 이미 상대가 대기 중이면 응답의 `waitingRoomId`가 채워져 곧바로 대기방으로 이동할 수 있다.
 */
import { apiRequest, storeGuestSessionId } from './http'
import type { GameName } from '../types/waitingRoom'
import type { MatchStatusResponse } from '../types/matchmaking'

export async function joinMatch(
  gameName: GameName,
  accessToken?: string | null,
): Promise<MatchStatusResponse> {
  const data = await apiRequest<MatchStatusResponse>('/match/join', {
    method: 'POST',
    body: { gameType: gameName },
    accessToken,
  })
  if (data.guestSessionId) {
    storeGuestSessionId(data.guestSessionId)
  }
  return data
}

export async function cancelMatch(
  accessToken?: string | null,
): Promise<void> {
  await apiRequest<MatchStatusResponse | null>('/match/cancel', {
    method: 'DELETE',
    accessToken,
  })
}
