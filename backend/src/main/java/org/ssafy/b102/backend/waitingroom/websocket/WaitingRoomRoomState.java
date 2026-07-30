package org.ssafy.b102.backend.waitingroom.websocket;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomParticipantResponse;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;

public record WaitingRoomRoomState(
	UUID roomId,
	String roomType,
	String gameName,
	String roomCode,
	String roomStatus,
	List<WaitingRoomParticipantResponse> participants,
	Instant createdAt
) {

	public static WaitingRoomRoomState from(WaitingRoomSnapshot snapshot) {
		return new WaitingRoomRoomState(
			snapshot.room().roomId(),
			snapshot.room().roomType().name(),
			snapshot.room().gameName().name(),
			snapshot.room().roomCode(),
			snapshot.room().roomStatus().name(),
			snapshot.participants().stream()
				.sorted(Comparator.comparingInt(participant -> participant.slotNo()))
				.map(WaitingRoomParticipantResponse::from)
				.toList(),
			snapshot.room().createdAt()
		);
	}
}
