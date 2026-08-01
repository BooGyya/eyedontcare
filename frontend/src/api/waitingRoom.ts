/**
 * 대기방 REST 호출.
 *
 * invite(친구 초대) 대결 흐름: 방장이 방을 생성하면 `roomCode`(4자리)가 나오고, 참가자는 그 코드로
 * 참가한다. 두 응답 모두 이후 WebSocket 인증에 쓸 `participantKey`와, 게스트라면 `guestSessionId`를 준다.
 */
import { apiRequest, storeGuestSessionId } from './http'
import type {
  GameName,
  WaitingRoomCreateResponse,
  WaitingRoomJoinResponse,
} from '../types/waitingRoom'

export async function createInviteRoom(
  gameName: GameName,
  accessToken?: string | null,
): Promise<WaitingRoomCreateResponse> {
  const data = await apiRequest<WaitingRoomCreateResponse>('/waiting-rooms', {
    method: 'POST',
    body: { gameName },
    accessToken,
  })
  if (data.guestSessionId) {
    storeGuestSessionId(data.guestSessionId)
  }
  return data
}

export async function joinInviteRoom(
  roomCode: string,
  accessToken?: string | null,
): Promise<WaitingRoomJoinResponse> {
  const data = await apiRequest<WaitingRoomJoinResponse>(
    '/waiting-rooms/join',
    {
      method: 'POST',
      body: { roomCode },
      accessToken,
    },
  )
  if (data.guestSessionId) {
    storeGuestSessionId(data.guestSessionId)
  }
  return data
}

export async function leaveRoom(
  roomId: string,
  accessToken?: string | null,
): Promise<void> {
  await apiRequest<null>(`/waiting-rooms/${roomId}/leave`, {
    method: 'POST',
    accessToken,
  })
}
