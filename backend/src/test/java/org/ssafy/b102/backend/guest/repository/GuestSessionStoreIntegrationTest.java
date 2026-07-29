package org.ssafy.b102.backend.guest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.config.RedisConfig;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;
import tools.jackson.databind.json.JsonMapper;

@DataRedisTest
@Import({
	RedisConfig.class,
	GuestSessionStore.class,
	GuestSessionStoreIntegrationTest.TestConfig.class
})
class GuestSessionStoreIntegrationTest {

	private static final Duration SESSION_TTL = Duration.ofHours(24);
	private static final UUID GUEST_SESSION_ID =
		UUID.fromString("27868019-1a91-40d3-8536-a0e5dcf7e8cf");
	private static final String GUEST_KEY =
		"edc:test:guest:session:27868019-1a91-40d3-8536-a0e5dcf7e8cf";
	private static final String REFRESH_TOKEN_KEY =
		"edc:test:auth:refresh-token:27868019-1a91-40d3-8536-a0e5dcf7e8cf";

	@Autowired
	private GuestSessionStore guestSessionStore;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@AfterEach
	void tearDown() {
		redisTemplate.delete(GUEST_KEY);
		redisTemplate.delete(REFRESH_TOKEN_KEY);
	}

	@Test
	void storesAndRestoresJsonWithGuestNamespaceAndTwentyFourHourTtl() {
		Instant createdAt = Instant.parse("2026-07-30T12:00:00Z");
		GuestSession guestSession = new GuestSession(
			GUEST_SESSION_ID,
			"용감한수달0123",
			createdAt,
			createdAt.plus(SESSION_TTL)
		);

		assertThat(guestSessionStore.saveIfAbsent(
			GUEST_SESSION_ID,
			guestSession,
			SESSION_TTL
		)).isTrue();

		String stored = redisTemplate.opsForValue().get(GUEST_KEY);
		assertThat(stored)
			.contains("\"nickname\":\"용감한수달0123\"")
			.contains("\"createdAt\":\"2026-07-30T12:00:00Z\"")
			.contains("\"expiresAt\":\"2026-07-31T12:00:00Z\"")
			.doesNotContain("guestSessionId");
		assertThat(guestSessionStore.findById(GUEST_SESSION_ID))
			.contains(guestSession);
		assertThat(guestSessionStore.getRemainingTtl(GUEST_SESSION_ID))
			.hasValueSatisfying(ttl ->
				assertThat(ttl).isBetween(
					SESSION_TTL.minusSeconds(10),
					SESSION_TTL
				));
		assertThat(redisTemplate.hasKey(REFRESH_TOKEN_KEY)).isFalse();
	}

	@Test
	void returnsEmptyAfterRedisKeyExpires() throws InterruptedException {
		redisTemplate.opsForValue().set(GUEST_KEY, "{}", Duration.ofMillis(10));

		Thread.sleep(50);

		assertThat(guestSessionStore.findById(GUEST_SESSION_ID)).isEmpty();
	}

	@Test
	void convertsMalformedJsonToStoreUnavailable() {
		redisTemplate.opsForValue().set(GUEST_KEY, "not-json", Duration.ofMinutes(1));

		assertThatThrownBy(() -> guestSessionStore.findById(GUEST_SESSION_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GuestSessionErrorCode.GUEST_SESSION_STORE_UNAVAILABLE));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		RedisKeyBuilder redisKeyBuilder() {
			return new RedisKeyBuilder("test");
		}

		@Bean
		JsonMapper jsonMapper() {
			return JsonMapper.builder().findAndAddModules().build();
		}
	}
}
