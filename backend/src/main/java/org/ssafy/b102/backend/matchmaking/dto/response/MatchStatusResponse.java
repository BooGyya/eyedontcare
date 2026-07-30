package org.ssafy.b102.backend.matchmaking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.entity.MatchmakingEntry;

/**
 * 매칭 신청·취소 응답.
 *
 * <p>API 명세서의 응답 예시는 {@code matchStatus} 하나만 담고 있으나, 팀 논의로 필드를 넓혔다.
 * WebSocket({@code /ws/match})이 붙기 전까지는 이 응답이 클라이언트가 매칭 결과를 알 수 있는
 * 유일한 경로이고, 붙은 뒤에도 푸시를 놓친 경우의 fallback으로 쓸 수 있다.
 *
 * <p>{@code waitingRoomId}는 대기방 ID만 담는다. 실제 이동 지시는 WebSocket의
 * {@code MATCH_SUCCESS} 이벤트가 담당한다.
 */
public record MatchStatusResponse(
	String participantKey,
	GameName gameType,
	MatchStatus matchStatus,
	UUID waitingRoomId,
	Instant queuedAt,

	/**
	 * 게스트 참가자에게만 내려간다. 프론트가 이후 요청·WebSocket에서 같은 세션을 재사용하도록.
	 * 회원 응답에는 포함되지 않는다.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	UUID guestSessionId,

	@JsonInclude(JsonInclude.Include.NON_NULL)
	String guestNickname
) {

	public static MatchStatusResponse from(MatchmakingEntry entry) {
		return new MatchStatusResponse(
			entry.participantKey(),
			entry.gameType(),
			entry.matchStatus(),
			entry.waitingRoomId(),
			entry.queuedAt(),
			null,
			null
		);
	}

	/**
	 * 취소 응답. entry는 이미 삭제되었으므로 삭제 직전 값으로 만든다.
	 */
	public static MatchStatusResponse cancelled(MatchmakingEntry entry) {
		return new MatchStatusResponse(
			entry.participantKey(),
			entry.gameType(),
			MatchStatus.CANCELLED,
			null,
			entry.queuedAt(),
			null,
			null
		);
	}

	/**
	 * 게스트 세션 정보를 얹은 사본을 만든다. 회원 응답에는 쓰지 않는다.
	 */
	public MatchStatusResponse withGuest(UUID guestSessionId, String guestNickname) {
		return new MatchStatusResponse(
			participantKey,
			gameType,
			matchStatus,
			waitingRoomId,
			queuedAt,
			guestSessionId,
			guestNickname
		);
	}
}
