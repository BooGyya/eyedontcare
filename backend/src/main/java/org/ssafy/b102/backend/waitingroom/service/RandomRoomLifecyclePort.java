package org.ssafy.b102.backend.waitingroom.service;

import java.util.List;
import java.util.UUID;

public interface RandomRoomLifecyclePort {

	void markParticipantEntered(UUID roomId, String participantKey);

	void completeRandomRoom(UUID roomId, List<String> participantKeys);

	/**
	 * RANDOM 대기방 입장·인증 실패 시 해당 roomId의 매칭 entry만 best-effort로 정리한다.
	 * INVITE 방이나 다른 roomId의 entry에는 영향을 주지 않는 멱등 연산이다.
	 */
	default void cleanupFailedParticipant(UUID roomId, String participantKey) {
		// Matchmaking adapter가 없는 테스트·과도기에는 정리할 대상이 없다.
	}

	/**
	 * 정상 RANDOM room leave가 확정된 뒤 같은 room의 entry만 정리한다.
	 * 인증 실패 cleanup과 책임을 분리하며 다른 방 또는 새 매칭 entry는 보존한다.
	 */
	default void cleanupParticipantAfterLeave(UUID roomId, String participantKey) {
		// Matchmaking adapter가 없는 테스트/구성에서는 정리할 외부 상태가 없다.
	}

	/**
	 * 종료된 이전 RANDOM 방의 실제 client disconnect가 확인되면 그 방에서 만든 자동 재매칭만 정리한다.
	 * 서버 주도 close나 새 매칭 entry에는 적용하지 않는다.
	 */
	default void cleanupRematchAfterPreviousRoomDisconnect(
		UUID previousRoomId,
		String participantKey
	) {
		// Matchmaking adapter가 없는 테스트/구성에서는 정리할 외부 상태가 없다.
	}
}
