package org.ssafy.b102.backend.waitingroom.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitingRoomJoinResponse(
	UUID roomId,
	String roomType,
	String gameName,
	String roomCode,
	String roomStatus,
	List<WaitingRoomParticipantResponse> participants,
	Instant createdAt,
	UUID guestSessionId,
	String guestNickname,
	String openviduUrl,
	String token
) {

	public static WaitingRoomJoinResponse of(
		WaitingRoomSnapshot snapshot,
		ResolvedWaitingRoomParticipant identity,
		String openviduUrl,
		String token
	) {
		return new WaitingRoomJoinResponse(
			snapshot.room().roomId(),
			snapshot.room().roomType().name(),
			snapshot.room().gameName().name(),
			snapshot.room().roomCode(),
			snapshot.room().roomStatus().name(),
			snapshot.participants().stream()
				.sorted(Comparator.comparingInt(participant -> participant.slotNo()))
				.map(WaitingRoomParticipantResponse::from)
				.toList(),
			snapshot.room().createdAt(),
			identity.guestSessionId(),
			identity.guestNickname(),
			openviduUrl,
			token
		);
	}
}
