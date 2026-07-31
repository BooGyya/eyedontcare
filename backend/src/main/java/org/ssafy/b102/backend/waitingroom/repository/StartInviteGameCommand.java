package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record StartInviteGameCommand(
	UUID roomId,
	String roomCode,
	String participantKey,
	UUID countdownId,
	Instant countdownEndsAt,
	int maxParticipants,
	Duration activeTtl
) {
}
