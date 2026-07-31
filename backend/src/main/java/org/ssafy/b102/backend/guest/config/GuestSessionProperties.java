package org.ssafy.b102.backend.guest.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.guest")
public record GuestSessionProperties(Duration sessionTtl) {

	public GuestSessionProperties {
		if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
			throw new IllegalArgumentException("Guest session TTL must be positive");
		}
	}
}
