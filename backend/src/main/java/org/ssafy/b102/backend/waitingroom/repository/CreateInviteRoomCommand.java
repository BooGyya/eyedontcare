package org.ssafy.b102.backend.waitingroom.repository;

import java.time.Duration;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;

public record CreateInviteRoomCommand(
	WaitingRoom room,
	WaitingRoomParticipant participant,
	Duration ttl
) {
}
