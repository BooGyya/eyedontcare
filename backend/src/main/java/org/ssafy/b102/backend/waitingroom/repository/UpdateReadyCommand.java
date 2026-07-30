package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import java.util.UUID;

public record UpdateReadyCommand(
	UUID roomId,
	String roomCode,
	String participantKey,
	boolean ready,
	int maxParticipants,
	Duration activeTtl
) {
}
