package org.ssafy.b102.backend.ping.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.ping.dto.PingResponse;

class PingServiceTest {

	private final PingService pingService = new PingService();

	@Test
	void pingReturnsPongResponse() {
		PingResponse response = pingService.ping();

		assertThat(response.status()).isEqualTo("pong");
	}
}
