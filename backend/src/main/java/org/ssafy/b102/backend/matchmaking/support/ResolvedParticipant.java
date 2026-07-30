package org.ssafy.b102.backend.matchmaking.support;

import java.util.UUID;

/**
 * 매칭 참가자의 신원을 푼 결과.
 *
 * <p>회원은 {@code USER:{userId}} 키만 가진다. 게스트는 {@code GUEST:{uuid}} 키와 함께
 * 세션 id·닉네임을 가지며, 이 둘은 매칭 응답으로 내려보내 프론트가 다음 요청에 재사용한다.
 * 닉네임은 표시용이고 식별은 항상 {@code guestSessionId} 기준이다.
 */
public record ResolvedParticipant(String participantKey, UUID guestSessionId, String guestNickname) {

	public static ResolvedParticipant member(String participantKey) {
		return new ResolvedParticipant(participantKey, null, null);
	}

	public static ResolvedParticipant guest(String participantKey, UUID guestSessionId, String guestNickname) {
		return new ResolvedParticipant(participantKey, guestSessionId, guestNickname);
	}

	public boolean isGuest() {
		return guestSessionId != null;
	}
}
