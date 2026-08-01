package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import java.util.UUID;

public record LeaveWaitingRoomCommand(
	UUID roomId,
	String roomCode,
	String participantKey,
	int maxParticipants,
	Duration activeTtl,
	Duration closedTtl
) {
}
