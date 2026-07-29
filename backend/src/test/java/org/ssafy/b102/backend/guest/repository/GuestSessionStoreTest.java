package org.ssafy.b102.backend.guest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GuestSessionStoreTest {

	private static final UUID GUEST_SESSION_ID =
		UUID.fromString("27868019-1a91-40d3-8536-a0e5dcf7e8cf");
	private static final String KEY =
		"edc:test:guest:session:27868019-1a91-40d3-8536-a0e5dcf7e8cf";
	private static final Duration TTL = Duration.ofHours(24);
	private static final Instant CREATED_AT = Instant.parse("2026-07-30T12:00:00Z");
	private static final String JSON =
		"{\"nickname\":\"용감한수달0123\","
			+ "\"createdAt\":\"2026-07-30T12:00:00Z\","
			+ "\"expiresAt\":\"2026-07-31T12:00:00Z\"}";

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private JsonMapper jsonMapper;

	private GuestSessionStore guestSessionStore;
	private GuestSession guestSession;

	@BeforeEach
	void setUp() {
		guestSessionStore = new GuestSessionStore(
			redisTemplate,
			new RedisKeyBuilder("test"),
			jsonMapper
		);
		guestSession = new GuestSession(
			GUEST_SESSION_ID,
			"용감한수달0123",
			CREATED_AT,
			CREATED_AT.plus(TTL)
		);
	}

	@Test
	void storesJsonWithNamespaceAndTtlWhenKeyIsAbsent() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(jsonMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
			.thenReturn(JSON);
		when(valueOperations.setIfAbsent(KEY, JSON, TTL)).thenReturn(true);

		assertThat(guestSessionStore.saveIfAbsent(GUEST_SESSION_ID, guestSession, TTL))
			.isTrue();

		verify(valueOperations).setIfAbsent(KEY, JSON, TTL);
	}

	@Test
	void findsGuestSessionOnlyWhenValueAndPositiveTtlExist() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(KEY)).thenReturn(JSON);
		when(redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(3_600_000L);
		when(jsonMapper.readValue(JSON, GuestSessionStore.StoredGuestSession.class))
			.thenReturn(new GuestSessionStore.StoredGuestSession(
				guestSession.nickname(),
				guestSession.createdAt(),
				guestSession.expiresAt()
			));

		Optional<GuestSession> found = guestSessionStore.findById(GUEST_SESSION_ID);

		assertThat(found).contains(guestSession);
	}

	@Test
	void returnsEmptyWhenSessionIsMissingExpiredOrHasNoTtl() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(KEY)).thenReturn(null, JSON, JSON);
		when(redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(-2L, -1L);

		assertThat(guestSessionStore.findById(GUEST_SESSION_ID)).isEmpty();
		assertThat(guestSessionStore.findById(GUEST_SESSION_ID)).isEmpty();
		assertThat(guestSessionStore.findById(GUEST_SESSION_ID)).isEmpty();
	}

	@Test
	void returnsActualRemainingTtl() {
		when(redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(5_000L);

		assertThat(guestSessionStore.getRemainingTtl(GUEST_SESSION_ID))
			.contains(Duration.ofSeconds(5));
	}

	@Test
	void convertsRedisAndJsonFailuresToStoreUnavailable() {
		when(redisTemplate.opsForValue())
			.thenThrow(new RedisConnectionFailureException("redis unavailable"));

		assertThatThrownBy(() -> guestSessionStore.findById(GUEST_SESSION_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GuestSessionErrorCode.GUEST_SESSION_STORE_UNAVAILABLE));
	}

}
