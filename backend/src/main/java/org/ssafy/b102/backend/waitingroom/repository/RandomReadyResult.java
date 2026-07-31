package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Instant;
import java.util.UUID;

public record RandomReadyResult(
	Status status,
	UUID countdownId,
	Instant countdownEndsAt
) {

	public enum Status {
		UPDATED,
		UNCHANGED,
		COUNTDOWN_STARTED,
		ALREADY_COUNTDOWN,
		ROOM_NOT_FOUND,
		PARTICIPANT_NOT_FOUND,
		CALIBRATION_REQUIRED,
		STATE_CHANGE_NOT_ALLOWED,
		CORRUPTED
	}
}
