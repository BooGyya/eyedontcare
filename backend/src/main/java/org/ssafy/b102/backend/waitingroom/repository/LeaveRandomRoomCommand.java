package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import java.util.UUID;

public record LeaveRandomRoomCommand(
	UUID roomId,
	String participantKey,
	Duration closedTtl
) {
}
