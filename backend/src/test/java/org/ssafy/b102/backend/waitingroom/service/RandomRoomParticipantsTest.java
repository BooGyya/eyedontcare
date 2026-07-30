package org.ssafy.b102.backend.waitingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RandomRoomParticipantsTest {

	@Test
	void preservesParticipantOrder() {
		RandomRoomParticipants participants =
			RandomRoomParticipants.from(List.of("USER:1", "GUEST:guest-id"));

		assertThat(participants.firstParticipantKey()).isEqualTo("USER:1");
		assertThat(participants.secondParticipantKey()).isEqualTo("GUEST:guest-id");
	}

	@Test
	void rejectsInvalidParticipantPairs() {
		assertThatThrownBy(() ->
			RandomRoomParticipants.from(List.of("USER:1")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			RandomRoomParticipants.from(List.of("USER:1", "USER:1")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			RandomRoomParticipants.from(java.util.Arrays.asList("USER:1", null)))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
