package org.ssafy.b102.backend.guest.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GuestSession(
	UUID guestSessionId,
	String nickname,
	Instant createdAt,
	Instant expiresAt
) {

	public GuestSession {
		Objects.requireNonNull(guestSessionId, "Guest session ID must not be null");
		if (nickname == null || nickname.isBlank()) {
			throw new IllegalArgumentException("Guest nickname must not be blank");
		}
		Objects.requireNonNull(createdAt, "Guest session creation time must not be null");
		Objects.requireNonNull(expiresAt, "Guest session expiration time must not be null");
		if (!expiresAt.isAfter(createdAt)) {
			throw new IllegalArgumentException("Guest session expiration time must be after creation time");
		}
	}
}
