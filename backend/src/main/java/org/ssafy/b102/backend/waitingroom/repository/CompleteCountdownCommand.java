package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record CompleteCountdownCommand(
	UUID roomId,
	String roomCode,
	UUID countdownId,
	Instant countdownEndsAt,
	int maxParticipants,
	Duration activeTtl
) {
}
