package org.ssafy.b102.backend.waitingroom.service;

import java.util.List;

public record RandomRoomParticipants(
	String firstParticipantKey,
	String secondParticipantKey
) {

	public static RandomRoomParticipants from(List<String> participantKeys) {
		if (
			participantKeys == null ||
			participantKeys.size() != 2 ||
			participantKeys.get(0) == null ||
			participantKeys.get(0).isBlank() ||
			participantKeys.get(1) == null ||
			participantKeys.get(1).isBlank() ||
			participantKeys.get(0).equals(participantKeys.get(1))
		) {
			throw new IllegalArgumentException("Random room requires two distinct participants");
		}
		return new RandomRoomParticipants(participantKeys.get(0), participantKeys.get(1));
	}
}
