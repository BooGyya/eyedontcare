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
}
