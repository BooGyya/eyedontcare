package org.ssafy.b102.backend.guest.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;

class GuestParticipantKeyTest {

	private static final UUID GUEST_SESSION_ID =
		UUID.fromString("27868019-1a91-40d3-8536-a0e5dcf7e8cf");

	@Test
	void parsesGuestParticipantKey() {
		GuestParticipantKey participantKey =
			GuestParticipantKey.parse("GUEST:" + GUEST_SESSION_ID);

		assertThat(participantKey.guestSessionId()).isEqualTo(GUEST_SESSION_ID);
		assertThat(participantKey.value()).isEqualTo("GUEST:" + GUEST_SESSION_ID);
	}

	@Test
	void rejectsMemberParticipantKey() {
		assertInvalid(() -> GuestParticipantKey.parse("USER:1"));
	}

	@Test
	void rejectsInvalidUuid() {
		assertInvalid(() -> GuestParticipantKey.parse("GUEST:not-a-uuid"));
	}

	@Test
	void rejectsBlankIdentifier() {
		assertInvalid(() -> GuestParticipantKey.parse("GUEST:"));
	}

	private static void assertInvalid(Runnable invocation) {
		assertThatThrownBy(invocation::run)
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GuestSessionErrorCode.INVALID_GUEST_SESSION));
	}
}
