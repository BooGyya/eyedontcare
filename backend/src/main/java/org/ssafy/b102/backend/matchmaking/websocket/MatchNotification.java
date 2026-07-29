package org.ssafy.b102.backend.matchmaking.websocket;

import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;

/**
 * 클라이언트로 내보내는 매칭 이벤트 프레임.
 *
 * <p>명세서가 정한 형태는 {@code {"type":"MATCH_SUCCESS","roomId":"...","gameType":"EYEFIGHT"}}이다.
 * STOMP가 아니라 raw WebSocket을 쓰는 이유가 이 형태를 그대로 내보내기 위해서다.
 * STOMP를 쓰면 프레임에 STOMP 헤더가 붙어 명세와 달라진다.
 */
public record MatchNotification(String type, String roomId, String gameType) {

	private static final String MATCH_SUCCESS = "MATCH_SUCCESS";

	public static MatchNotification matchSuccess(UUID roomId, GameName gameType) {
		return new MatchNotification(MATCH_SUCCESS, roomId.toString(), gameType.name());
	}
}
