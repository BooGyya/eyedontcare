package org.ssafy.b102.backend.waitingroom.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;

public record WaitingRoom(
	UUID roomId,
	RoomType roomType,
	GameName gameName,
	String roomCode,
	RoomStatus roomStatus,
	Instant createdAt,
	UUID countdownId,
	Instant countdownEndsAt
) {

	public WaitingRoom(
		UUID roomId,
		RoomType roomType,
		GameName gameName,
		String roomCode,
		RoomStatus roomStatus,
		Instant createdAt
	) {
		this(
			roomId,
			roomType,
			gameName,
			roomCode,
			roomStatus,
			createdAt,
			null,
			null
		);
	}

	public WaitingRoom {
		Objects.requireNonNull(roomId);
		Objects.requireNonNull(roomType);
		Objects.requireNonNull(gameName);
		Objects.requireNonNull(roomStatus);
		Objects.requireNonNull(createdAt);
		if (
			(roomType == RoomType.INVITE && roomCode == null) ||
			(roomType == RoomType.RANDOM && roomCode != null)
		) {
			throw new IllegalArgumentException("Invalid room code for room type");
		}
		if (
			roomStatus == RoomStatus.COUNTDOWN &&
			(countdownId == null || countdownEndsAt == null)
		) {
			throw new IllegalArgumentException(
				"Countdown room requires countdown metadata"
			);
		}
	}
}
