package org.ssafy.b102.backend.waitingroom.websocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InMemoryWaitingRoomWebSocketSessionRegistry
	implements WaitingRoomWebSocketSessionRegistry {

	private final Map<String, WaitingRoomConnectionContext> contextsBySession =
		new HashMap<>();
	private final Map<RoomParticipantKey, WaitingRoomConnectionContext>
		contextsByParticipant = new HashMap<>();

	@Override
	public synchronized boolean registerIfAbsent(
		WaitingRoomConnectionContext context
	) {
		RoomParticipantKey key = new RoomParticipantKey(
			context.roomId(),
			context.participantKey()
		);
		if (
			contextsBySession.containsKey(context.sessionId()) ||
			contextsByParticipant.containsKey(key)
		) {
			return false;
		}

		contextsBySession.put(context.sessionId(), context);
		contextsByParticipant.put(key, context);
		return true;
	}

	@Override
	public synchronized Optional<WaitingRoomConnectionContext> findBySessionId(
		String sessionId
	) {
		return Optional.ofNullable(contextsBySession.get(sessionId));
	}

	@Override
	public synchronized List<WaitingRoomConnectionContext> findByRoomId(
		UUID roomId
	) {
		return contextsBySession.values().stream()
			.filter(context -> context.roomId().equals(roomId))
			.toList();
	}

	@Override
	public synchronized Optional<WaitingRoomConnectionContext>
	findByRoomAndParticipant(UUID roomId, String participantKey) {
		return Optional.ofNullable(
			contextsByParticipant.get(
				new RoomParticipantKey(roomId, participantKey)
			)
		);
	}

	@Override
	public synchronized Optional<WaitingRoomConnectionContext> unregister(
		String sessionId
	) {
		WaitingRoomConnectionContext removed = contextsBySession.remove(sessionId);
		if (removed == null) {
			return Optional.empty();
		}
		contextsByParticipant.remove(
			new RoomParticipantKey(removed.roomId(), removed.participantKey()),
			removed
		);
		return Optional.of(removed);
	}

	@Override
	public synchronized void markSuppressLeave(String sessionId) {
		WaitingRoomConnectionContext context = contextsBySession.get(sessionId);
		if (context != null) {
			context.markSuppressLeave();
		}
	}

	private record RoomParticipantKey(UUID roomId, String participantKey) {
	}
}
