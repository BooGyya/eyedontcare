package org.ssafy.b102.backend.waitingroom.dto.response;

import java.time.Instant;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;

public record WaitingRoomParticipantResponse(
	String participantKey,
	String displayName,
	String roomRole,
	int slotNo,
	boolean isReady,
	String calibrationStatus,
	Instant joinedAt
) {

	public static WaitingRoomParticipantResponse from(WaitingRoomParticipant participant) {
		return new WaitingRoomParticipantResponse(
			participant.participantKey(),
			participant.displayName(),
			participant.roomRole().name(),
			participant.slotNo(),
			participant.isReady(),
			participant.calibrationStatus().name(),
			participant.joinedAt()
		);
	}
}
