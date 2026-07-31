package org.ssafy.b102.backend.waitingroom.websocket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitingRoomWebSocketSessionRegistry {

	boolean registerIfAbsent(WaitingRoomConnectionContext context);

	Optional<WaitingRoomConnectionContext> findBySessionId(String sessionId);

	List<WaitingRoomConnectionContext> findByRoomId(UUID roomId);

	Optional<WaitingRoomConnectionContext> findByRoomAndParticipant(
		UUID roomId,
		String participantKey
	);

	Optional<WaitingRoomConnectionContext> unregister(String sessionId);

	void markSuppressLeave(String sessionId);
}
