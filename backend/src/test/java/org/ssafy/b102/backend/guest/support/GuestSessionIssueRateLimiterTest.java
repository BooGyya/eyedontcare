package org.ssafy.b102.backend.guest.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.config.GuestSessionIssueRateLimitProperties;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestSessionIssueRateLimiterTest {

	private static final String CLIENT_IP = "203.0.113.7";
	private static final String KEY = "edc:test:guest:issue-rate:203.0.113.7";
	private static final int LIMIT = 3;
	private static final Duration WINDOW = Duration.ofMinutes(1);

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private GuestSessionIssueRateLimiter rateLimiter;

	@BeforeEach
	void setUp() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		rateLimiter = new GuestSessionIssueRateLimiter(
			redisTemplate,
			new RedisKeyBuilder("test"),
			new GuestSessionIssueRateLimitProperties(LIMIT, WINDOW)
		);
	}

	@Test
	void allowsRequestsUpToTheLimit() {
		when(valueOperations.increment(KEY)).thenReturn((long) LIMIT);
		when(redisTemplate.getExpire(KEY)).thenReturn(30L);

		assertThatCode(() -> rateLimiter.check(CLIENT_IP)).doesNotThrowAnyException();
	}

	@Test
	void rejectsRequestsPastTheLimit() {
		when(valueOperations.increment(KEY)).thenReturn(LIMIT + 1L);
		when(redisTemplate.getExpire(KEY)).thenReturn(30L);

		assertThatThrownBy(() -> rateLimiter.check(CLIENT_IP))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(GuestSessionErrorCode.GUEST_SESSION_ISSUE_RATE_LIMITED);
	}

	@Test
	void startsTheWindowOnTheFirstRequest() {
		when(valueOperations.increment(KEY)).thenReturn(1L);

		rateLimiter.check(CLIENT_IP);

		verify(redisTemplate).expire(KEY, WINDOW);
	}

	/** TTL 설정이 한 번 실패해 만료 없는 키가 남으면, 그 클라이언트가 영구히 막히면 안 된다. */
	@Test
	void restoresTheWindowWhenCounterHasNoExpiry() {
		when(valueOperations.increment(KEY)).thenReturn(2L);
		when(redisTemplate.getExpire(KEY)).thenReturn(-1L);

		rateLimiter.check(CLIENT_IP);

		verify(redisTemplate).expire(KEY, WINDOW);
	}

	@Test
	void keepsTheExistingWindowWhileCounting() {
		when(valueOperations.increment(KEY)).thenReturn(2L);
		when(redisTemplate.getExpire(KEY)).thenReturn(30L);

		rateLimiter.check(CLIENT_IP);

		verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
	}

	/** 제한은 안전망일 뿐이다. Redis가 죽으면 세션 저장이 어차피 실패하므로 여기서 막지 않는다. */
	@Test
	void allowsRequestWhenRedisIsUnavailable() {
		when(valueOperations.increment(KEY))
			.thenThrow(new RedisConnectionFailureException("down"));

		assertThatCode(() -> rateLimiter.check(CLIENT_IP)).doesNotThrowAnyException();
	}
}
