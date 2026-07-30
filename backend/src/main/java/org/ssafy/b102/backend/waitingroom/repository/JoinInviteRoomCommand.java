package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record JoinInviteRoomCommand(
	UUID roomId,
	String roomCode,
	String participantKey,
	String displayName,
	Instant joinedAt,
	int maxParticipants,
	Duration ttl
) {
}
