package org.ssafy.b102.backend.global.openvidu;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * OpenVidu(LiveKit 호환) 미디어 접속 토큰(JWT)을 발급한다.
 *
 * <p>api-secret으로 로컬 서명만 하므로 OpenVidu 서버와 통신할 필요가 없다. 단, OpenVidu 서버와
 * 동일한 key/secret이어야 서버가 토큰을 검증한다. LiveKit 라이브러리 의존성을 이 경계 클래스
 * 안에만 두어 상위 계층이 미디어 서버 구현에 직접 묶이지 않게 한다.
 */
@Service
public class LiveKitTokenService {

	private final String url;
	private final String apiKey;
	private final String apiSecret;

	public LiveKitTokenService(
		@Value("${openvidu.url}") String url,
		@Value("${openvidu.api-key}") String apiKey,
		@Value("${openvidu.api-secret}") String apiSecret
	) {
		this.url = url;
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
	}

	/**
	 * 브라우저가 접속할 미디어 서버 WSS 주소.
	 */
	public String url() {
		return url;
	}

	/**
	 * 참가자 한 명이 지정한 방에 접속할 미디어 토큰을 발급한다.
	 *
	 * @param participantKey 미디어 서버에서 참가자를 식별하는 identity (대기방 participantKey 재사용)
	 * @param displayName 다른 참가자에게 표시할 이름
	 * @param roomName 접속할 미디어 방 이름 (대기방 roomId 재사용)
	 */
	public String issueToken(String participantKey, String displayName, String roomName) {
		AccessToken token = new AccessToken(apiKey, apiSecret);
		token.setName(displayName);
		token.setIdentity(participantKey);
		token.addGrants(new RoomJoin(true), new RoomName(roomName));
		return token.toJwt();
	}
}
