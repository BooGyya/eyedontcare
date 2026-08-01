package org.ssafy.b102.backend.guest.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class GuestSessionStore {

	private static final String DOMAIN = "guest";
	private static final String RESOURCE = "session";

	private final StringRedisTemplate redisTemplate;
	private final RedisKeyBuilder redisKeyBuilder;
	private final JsonMapper jsonMapper;

	public GuestSessionStore(
		StringRedisTemplate redisTemplate,
		RedisKeyBuilder redisKeyBuilder,
		JsonMapper jsonMapper
	) {
		this.redisTemplate = redisTemplate;
		this.redisKeyBuilder = redisKeyBuilder;
		this.jsonMapper = jsonMapper;
	}

	public boolean saveIfAbsent(
		UUID guestSessionId,
		GuestSession guestSession,
		Duration ttl
	) {
		validateId(guestSessionId);
		validateTtl(ttl);

		try {
			Boolean saved = redisTemplate.opsForValue().setIfAbsent(
				createKey(guestSessionId),
				jsonMapper.writeValueAsString(StoredGuestSession.from(guestSession)),
				ttl
			);

			if (saved == null) {
				throw storeUnavailable();
			}

			return saved;
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	public Optional<GuestSession> findById(UUID guestSessionId) {
		validateId(guestSessionId);

		try {
			String key = createKey(guestSessionId);
			String stored = redisTemplate.opsForValue().get(key);
			if (stored == null || getRemainingTtl(key).isEmpty()) {
				return Optional.empty();
			}

			StoredGuestSession storedGuestSession =
				jsonMapper.readValue(stored, StoredGuestSession.class);

			return Optional.of(storedGuestSession.toGuestSession(guestSessionId));
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	public Optional<Duration> getRemainingTtl(UUID guestSessionId) {
		validateId(guestSessionId);

		try {
			return getRemainingTtl(createKey(guestSessionId));
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	private Optional<Duration> getRemainingTtl(String key) {
		Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
		if (ttlMillis == null || ttlMillis <= 0) {
			return Optional.empty();
		}

		return Optional.of(Duration.ofMillis(ttlMillis));
	}

	private String createKey(UUID guestSessionId) {
		return redisKeyBuilder.build(DOMAIN, RESOURCE, guestSessionId.toString());
	}

	private static void validateId(UUID guestSessionId) {
		if (guestSessionId == null) {
			throw new BusinessException(GuestSessionErrorCode.INVALID_GUEST_SESSION);
		}
	}

	private static void validateTtl(Duration ttl) {
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw storeUnavailable();
		}
	}

	private static BusinessException storeUnavailable() {
		return new BusinessException(GuestSessionErrorCode.GUEST_SESSION_STORE_UNAVAILABLE);
	}

	record StoredGuestSession(
		String nickname,
		java.time.Instant createdAt,
		java.time.Instant expiresAt
	) {

		private static StoredGuestSession from(GuestSession guestSession) {
			return new StoredGuestSession(
				guestSession.nickname(),
				guestSession.createdAt(),
				guestSession.expiresAt()
			);
		}

		private GuestSession toGuestSession(UUID guestSessionId) {
			return new GuestSession(guestSessionId, nickname, createdAt, expiresAt);
		}
	}
}
