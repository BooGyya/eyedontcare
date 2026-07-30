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
	Instant createdAt
) {

	public WaitingRoom {
		Objects.requireNonNull(roomId);
		Objects.requireNonNull(roomType);
		Objects.requireNonNull(gameName);
		Objects.requireNonNull(roomCode);
		Objects.requireNonNull(roomStatus);
		Objects.requireNonNull(createdAt);
	}
}
