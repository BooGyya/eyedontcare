package org.ssafy.b102.backend.waitingroom.entity;

import java.time.Instant;
import java.util.Objects;

public record WaitingRoomParticipant(
	String participantKey,
	String displayName,
	RoomRole roomRole,
	int slotNo,
	boolean isReady,
	CalibrationStatus calibrationStatus,
	Instant joinedAt
) {

	public WaitingRoomParticipant {
		Objects.requireNonNull(participantKey);
		Objects.requireNonNull(displayName);
		Objects.requireNonNull(roomRole);
		Objects.requireNonNull(calibrationStatus);
		Objects.requireNonNull(joinedAt);
	}
}
