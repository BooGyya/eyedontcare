package org.ssafy.b102.backend.guest.support;

import java.util.UUID;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;

public record GuestParticipantKey(UUID guestSessionId) {

	private static final String PREFIX = "GUEST:";

	public GuestParticipantKey {
		if (guestSessionId == null) {
			throw invalidGuestSession();
		}
	}

	public static GuestParticipantKey parse(String participantKey) {
		if (participantKey == null || !participantKey.startsWith(PREFIX)) {
			throw invalidGuestSession();
		}

		String identifier = participantKey.substring(PREFIX.length());
		if (identifier.isBlank()) {
			throw invalidGuestSession();
		}

		try {
			return new GuestParticipantKey(UUID.fromString(identifier));
		} catch (IllegalArgumentException exception) {
			throw invalidGuestSession();
		}
	}

	public String value() {
		return PREFIX + guestSessionId;
	}

	private static BusinessException invalidGuestSession() {
		return new BusinessException(GuestSessionErrorCode.INVALID_GUEST_SESSION);
	}
}
