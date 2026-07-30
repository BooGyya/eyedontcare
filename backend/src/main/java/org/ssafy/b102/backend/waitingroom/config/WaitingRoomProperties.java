package org.ssafy.b102.backend.waitingroom.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.waiting-room")
public record WaitingRoomProperties(
	Duration activeTtl,
	Duration closedTtl,
	int maxParticipants,
	int inviteCodeMaxAttempts
) {

	public WaitingRoomProperties {
		requirePositive(activeTtl, "Active TTL");
		requirePositive(closedTtl, "Closed TTL");
		if (maxParticipants < 1) {
			throw new IllegalArgumentException("Max participants must be positive");
		}
		if (inviteCodeMaxAttempts < 1) {
			throw new IllegalArgumentException("Invite code max attempts must be positive");
		}
	}

	private static void requirePositive(Duration duration, String name) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
