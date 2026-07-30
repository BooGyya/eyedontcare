package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import java.util.List;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;

public record CreateRandomRoomCommand(
	WaitingRoom room,
	List<WaitingRoomParticipant> participants,
	Duration ttl
) {
}
