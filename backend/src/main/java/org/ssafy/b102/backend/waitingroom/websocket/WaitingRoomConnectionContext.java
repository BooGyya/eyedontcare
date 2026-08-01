package org.ssafy.b102.backend.waitingroom.websocket;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.socket.WebSocketSession;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;

public record WaitingRoomConnectionContext(
	String sessionId,
	UUID roomId,
	String participantKey,
	RoomRole roomRole,
	Instant connectedAt,
	WebSocketSession session,
	AtomicBoolean suppressLeave
) {

	public WaitingRoomConnectionContext(
		String sessionId,
		UUID roomId,
		String participantKey,
		RoomRole roomRole,
		Instant connectedAt,
		WebSocketSession session
	) {
		this(
			sessionId,
			roomId,
			participantKey,
			roomRole,
			connectedAt,
			session,
			new AtomicBoolean(false)
		);
	}

	public void markSuppressLeave() {
		suppressLeave.set(true);
	}

	public boolean isLeaveSuppressed() {
		return suppressLeave.get();
	}
}
