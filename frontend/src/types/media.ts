/**
 * WebRTC 미디어 세션 관련 공용 타입.
 *
 * 대기방 WebSocket의 `GAME_START` 이벤트로 백엔드가 참가자별 접속 정보를 내려주고,
 * 플레이 화면이 이 정보로 LiveKit(OpenVidu) 미디어 서버에 직접 연결한다.
 */

/** LiveKit(OpenVidu) 미디어 서버 접속에 필요한 최소 정보. */
export interface MediaSessionCredentials {
  /** 브라우저가 접속할 미디어 서버 WSS 주소. */
  openviduUrl: string
  /** 이 참가자 전용 접속 토큰(JWT). 참가자마다 다르다. */
  token: string
}

/** 대기방 `GAME_START` 이벤트의 data 페이로드. */
export interface GameStartPayload extends Partial<MediaSessionCredentials> {
  roomId: string
  gameName: string
  startedAt: string
}

/** 원격 참가자 한 명의 렌더링 상태. */
export interface RemoteMediaParticipant {
  identity: string
  name: string
  videoTrack: globalThis.MediaStreamTrack | null
  audioTrack: globalThis.MediaStreamTrack | null
}
