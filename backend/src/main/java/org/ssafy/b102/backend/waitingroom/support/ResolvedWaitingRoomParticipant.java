package org.ssafy.b102.backend.waitingroom.support;

import java.util.UUID;

public record ResolvedWaitingRoomParticipant(
	String participantKey,
	String displayName,
	UUID guestSessionId,
	String guestNickname
) {

	public static ResolvedWaitingRoomParticipant member(String participantKey, String displayName) {
		return new ResolvedWaitingRoomParticipant(participantKey, displayName, null, null);
	}

	public static ResolvedWaitingRoomParticipant guest(
		String participantKey,
		String displayName,
		UUID guestSessionId
	) {
		return new ResolvedWaitingRoomParticipant(
			participantKey,
			displayName,
			guestSessionId,
			displayName
		);
	}
}
