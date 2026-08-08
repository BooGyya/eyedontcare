package org.ssafy.b102.backend.guest.support;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.config.GuestSessionIssueRateLimitProperties;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;

/**
 * 게스트 세션 발급 요청을 클라이언트별로 제한한다.
 *
 * <p>Redis에 고정 창(fixed window) 카운터를 둔다. 창의 첫 요청에서 TTL을 걸고, 창 안의 요청
 * 수가 한도를 넘으면 거절한다. 창 경계에서 최대 2배까지 허용되는 건 이 방식의 알려진 한계지만,
 * 남용 방지용 안전망으로는 충분하다.
 *
 * <p>Redis에 문제가 생기면 <b>제한을 걸지 않고 통과시킨다</b>(fail-open). 이 카운터는 안전망일
 * 뿐이고, 정작 세션 저장 자체가 Redis를 쓰므로 Redis가 죽으면 어차피 발급이 실패한다. 여기서
 * 막아 봐야 사용자에게 더 헷갈리는 오류만 남는다.
 */
@Component
public class GuestSessionIssueRateLimiter {

	private static final String DOMAIN = "guest";
	private static final String RESOURCE = "issue-rate";
	/** TTL이 없는 키를 뜻하는 Redis 응답(-1: 만료 없음, -2: 키 없음). */
	private static final long NO_EXPIRY = 0L;

	private final StringRedisTemplate redisTemplate;
	private final RedisKeyBuilder redisKeyBuilder;
	private final int limit;
	private final Duration window;

	public GuestSessionIssueRateLimiter(
		StringRedisTemplate redisTemplate,
		RedisKeyBuilder redisKeyBuilder,
		GuestSessionIssueRateLimitProperties properties
	) {
		this.redisTemplate = redisTemplate;
		this.redisKeyBuilder = redisKeyBuilder;
		this.limit = properties.limit();
		this.window = properties.window();
	}

	/**
	 * 발급을 한 번 기록하고, 한도를 넘었으면 예외를 던진다.
	 *
	 * @throws BusinessException 한도를 초과한 경우
	 */
	public void check(String clientId) {
		Long count;
		try {
			count = increment(clientId);
		} catch (RuntimeException exception) {
			return;
		}

		if (count != null && count > limit) {
			throw new BusinessException(GuestSessionErrorCode.GUEST_SESSION_ISSUE_RATE_LIMITED);
		}
	}

	private Long increment(String clientId) {
		String key = redisKeyBuilder.build(DOMAIN, RESOURCE, clientId);
		Long count = redisTemplate.opsForValue().increment(key);

		// 창의 첫 요청이면 TTL을 건다. 이전 요청에서 TTL 설정이 실패했을 수도 있으므로, 만료가
		// 없는 키를 발견하면 여기서 되살린다 — 안 그러면 그 클라이언트가 영구히 막힌다.
		if (count != null && (count == 1L || expiresAt(key) <= NO_EXPIRY)) {
			redisTemplate.expire(key, window);
		}

		return count;
	}

	private long expiresAt(String key) {
		Long remainingSeconds = redisTemplate.getExpire(key);

		return remainingSeconds == null ? NO_EXPIRY : remainingSeconds;
	}
}
