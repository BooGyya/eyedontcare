package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import java.util.UUID;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;

public record UpdateCalibrationCommand(
	UUID roomId,
	String roomCode,
	String participantKey,
	CalibrationStatus calibrationStatus,
	int maxParticipants,
	Duration activeTtl
) {
}
