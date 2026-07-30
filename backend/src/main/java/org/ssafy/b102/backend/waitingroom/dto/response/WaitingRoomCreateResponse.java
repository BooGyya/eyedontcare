package org.ssafy.b102.backend.waitingroom.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitingRoomCreateResponse(
	UUID roomId,
	String roomType,
	String gameName,
	String roomCode,
	String roomStatus,
	WaitingRoomParticipantResponse participant,
	Instant createdAt,
	UUID guestSessionId,
	String guestNickname
) {

	public static WaitingRoomCreateResponse of(
		WaitingRoom room,
		WaitingRoomParticipant participant,
		ResolvedWaitingRoomParticipant identity
	) {
		return new WaitingRoomCreateResponse(
			room.roomId(),
			room.roomType().name(),
			room.gameName().name(),
			room.roomCode(),
			room.roomStatus().name(),
			WaitingRoomParticipantResponse.from(participant),
			room.createdAt(),
			identity.guestSessionId(),
			identity.guestNickname()
		);
	}
}
