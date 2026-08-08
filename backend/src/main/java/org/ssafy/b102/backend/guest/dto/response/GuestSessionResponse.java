package org.ssafy.b102.backend.guest.dto.response;

import java.time.Instant;
import java.util.UUID;
import org.ssafy.b102.backend.guest.entity.GuestSession;

/**
 * 게스트 세션 발급 응답.
 *
 * <p>클라이언트는 {@code guestSessionId}를 이후 요청의 {@code X-Guest-Session-Id} 헤더로 보낸다.
 * {@code nickname}은 결과 저장 시 서버가 다시 채우지만, 화면에 미리 보여줄 수 있도록 함께 준다.
 */
public record GuestSessionResponse(
	UUID guestSessionId,
	String nickname,
	Instant expiresAt
) {

	public static GuestSessionResponse from(GuestSession guestSession) {
		return new GuestSessionResponse(
			guestSession.guestSessionId(),
			guestSession.nickname(),
			guestSession.expiresAt()
		);
	}
}
