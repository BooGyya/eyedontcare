package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Instant;
import java.util.UUID;

public record StartInviteGameResult(
	Status status,
	UUID countdownId,
	Instant countdownEndsAt
) {

	public static StartInviteGameResult of(Status status) {
		return new StartInviteGameResult(status, null, null);
	}

	public enum Status {
		STARTED,
		ALREADY_COUNTDOWN,
		ROOM_NOT_FOUND,
		PARTICIPANT_NOT_FOUND,
		CALIBRATION_REQUIRED,
		GAME_START_FORBIDDEN,
		PARTICIPANTS_NOT_READY,
		STATE_CHANGE_NOT_ALLOWED,
		CORRUPTED
	}
}
