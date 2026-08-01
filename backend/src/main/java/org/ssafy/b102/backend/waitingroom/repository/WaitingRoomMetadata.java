package org.ssafy.b102.backend.waitingroom.repository;

import java.util.UUID;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;

public record WaitingRoomMetadata(
	UUID roomId,
	RoomType roomType,
	RoomStatus roomStatus,
	String roomCode
) {
}
