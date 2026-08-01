package org.ssafy.b102.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

/**
 * Redis 연결과 matchmaking 설계가 의존하는 명령 동작을 실제 Redis로 확인한다.
 *
 * <p>Redis 자체를 검증하는 것이 아니라, 매칭 큐 설계가 기대하는 계약
 * (ZADD NX가 기존 score를 유지한다, ZPOPMIN이 score까지 반환한다)을 고정하는 목적이다.
 */
@DataRedisTest
@Import(RedisConfig.class)
class RedisConnectionTest {

	private static final String KEY_PREFIX = "edc:test:global:connection:";

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	private String key;

	@AfterEach
	void tearDown() {
		if (key != null) {
			stringRedisTemplate.delete(key);
		}
	}

	@Test
	void storesAndReadsStringValue() {
		key = uniqueKey();

		stringRedisTemplate.opsForValue().set(key, "pong", Duration.ofSeconds(30));

		assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("pong");
	}

	/**
	 * 중복 매칭 신청 차단이 {@code SET NX}에 의존한다.
	 */
	@Test
	void setIfAbsentFailsWhenKeyAlreadyExists() {
		key = uniqueKey();

		Boolean first = stringRedisTemplate.opsForValue()
			.setIfAbsent(key, "first", Duration.ofSeconds(30));
		Boolean second = stringRedisTemplate.opsForValue()
			.setIfAbsent(key, "second", Duration.ofSeconds(30));

		assertThat(first).isTrue();
		assertThat(second).isFalse();
		assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("first");
	}

	/**
	 * 재신청이 최초 신청 시각을 덮어쓰지 않아야 한다.
	 */
	@Test
	void addIfAbsentKeepsOriginalScore() {
		key = uniqueKey();

		Boolean added = stringRedisTemplate.opsForZSet().addIfAbsent(key, "USER:1", 100.0);
		Boolean readded = stringRedisTemplate.opsForZSet().addIfAbsent(key, "USER:1", 500.0);

		assertThat(added).isTrue();
		assertThat(readded).isFalse();
		assertThat(stringRedisTemplate.opsForZSet().score(key, "USER:1")).isEqualTo(100.0);
	}

	/**
	 * 대기 순서가 빠른 두 명 선점과, 방 생성 실패 시 원래 score 복원에 필요한 동작이다.
	 */
	@Test
	void popMinReturnsLowestScoresWithScores() {
		key = uniqueKey();
		stringRedisTemplate.opsForZSet().addIfAbsent(key, "USER:1", 100.0);
		stringRedisTemplate.opsForZSet().addIfAbsent(key, "USER:2", 200.0);
		stringRedisTemplate.opsForZSet().addIfAbsent(key, "USER:3", 300.0);

		Set<TypedTuple<String>> popped = stringRedisTemplate.opsForZSet().popMin(key, 2);

		assertThat(popped).isNotNull().hasSize(2);
		List<TypedTuple<String>> ordered = popped.stream()
			.sorted(Comparator.comparingDouble(TypedTuple::getScore))
			.toList();
		assertThat(ordered.get(0).getValue()).isEqualTo("USER:1");
		assertThat(ordered.get(0).getScore()).isEqualTo(100.0);
		assertThat(ordered.get(1).getValue()).isEqualTo("USER:2");
		assertThat(ordered.get(1).getScore()).isEqualTo(200.0);
		assertThat(stringRedisTemplate.opsForZSet().size(key)).isEqualTo(1L);
	}

	@Test
	void popMinReturnsFewerMembersThanRequestedWhenQueueIsShort() {
		key = uniqueKey();
		stringRedisTemplate.opsForZSet().addIfAbsent(key, "USER:1", 100.0);

		Set<TypedTuple<String>> popped = stringRedisTemplate.opsForZSet().popMin(key, 2);

		assertThat(popped).isNotNull().hasSize(1);
	}

	private static String uniqueKey() {
		return KEY_PREFIX + UUID.randomUUID();
	}
}
