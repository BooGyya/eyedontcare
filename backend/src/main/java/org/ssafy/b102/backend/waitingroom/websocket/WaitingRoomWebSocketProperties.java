package org.ssafy.b102.backend.waitingroom.websocket;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.waiting-room.websocket")
public record WaitingRoomWebSocketProperties(
	Duration authTimeout,
	Duration sendTimeLimit,
	int bufferSizeLimit
) {

	public WaitingRoomWebSocketProperties {
		requirePositive(authTimeout, "Auth timeout");
		requirePositive(sendTimeLimit, "Send time limit");
		if (bufferSizeLimit < 1) {
			throw new IllegalArgumentException("Buffer size limit must be positive");
		}
	}

	private static void requirePositive(Duration duration, String name) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
