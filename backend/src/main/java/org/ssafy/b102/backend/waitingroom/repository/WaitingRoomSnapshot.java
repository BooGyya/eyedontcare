package org.ssafy.b102.backend.waitingroom.repository;

import java.util.List;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;

public record WaitingRoomSnapshot(
	WaitingRoom room,
	List<WaitingRoomParticipant> participants
) {

	public WaitingRoomSnapshot {
		participants = List.copyOf(participants);
	}
}
