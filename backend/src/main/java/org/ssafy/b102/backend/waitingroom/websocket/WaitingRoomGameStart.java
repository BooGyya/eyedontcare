package org.ssafy.b102.backend.waitingroom.websocket;

import java.time.Instant;
import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;

/**
 * 게임 시작 이벤트 페이로드.
 *
 * <p>{@code openviduUrl}/{@code token}은 WebRTC 미디어 서버 접속 정보이며 수신자마다 다르다
 * ({@code token}은 참가자별로 발급). 미디어 연동 전에는 두 값이 {@code null}일 수 있다.
 */
public record WaitingRoomGameStart(
	UUID roomId,
	GameName gameName,
	Instant startedAt,
	String openviduUrl,
	String token
) {
}
