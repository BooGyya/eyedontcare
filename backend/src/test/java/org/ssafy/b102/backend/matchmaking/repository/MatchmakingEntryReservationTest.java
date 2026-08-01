package org.ssafy.b102.backend.matchmaking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.config.RedisConfig;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.entity.MatchmakingEntry;

/**
 * 예약(MATCHING) 기반 원자 선점·finalize·보상 재등록을 실제 Redis로 검증한다.
 */
@DataRedisTest
@Import({RedisConfig.class, RedisKeyBuilder.class, MatchmakingEntryRepository.class})
class MatchmakingEntryReservationTest {

	private static final GameName GAME = GameName.HOCKEY;
	private static final String A = "USER:1";
	private static final String B = "USER:2";
	private static final Instant PAST = Instant.parse("2026-07-01T00:00:00Z");

	@Autowired
	private MatchmakingEntryRepository repository;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private RedisKeyBuilder redisKeyBuilder;

	@BeforeEach
	void setUp() {
		List.of(A, B).forEach(repository::delete);
		stringRedisTemplate.delete(queueKey());
	}

	@Test
	void reserveCandidatesMarksBothAsMatching() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));
		repository.enqueue(MatchmakingEntry.searching(B, GAME, PAST.plusSeconds(1)));
		UUID attempt = UUID.randomUUID();

		List<MatchmakingEntry> reserved = repository.reserveCandidates(GAME, attempt, 2);

		assertThat(reserved).hasSize(2);
		assertThat(repository.find(A)).get().satisfies(e -> {
			assertThat(e.matchStatus()).isEqualTo(MatchStatus.MATCHING);
			assertThat(e.matchAttemptId()).isEqualTo(attempt);
		});
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey())).isZero();
	}

	@Test
	void reserveCandidatesReturnsEmptyAndRequeuesWhenNotEnough() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));

		List<MatchmakingEntry> reserved = repository.reserveCandidates(GAME, UUID.randomUUID(), 2);

		assertThat(reserved).isEmpty();
		assertThat(repository.find(A)).get()
			.satisfies(e -> assertThat(e.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey())).isEqualTo(1L);
	}

	@Test
	void finalizeToRoomSetsEnteringRoomWhenSameAttempt() {
		enqueuePair();
		UUID attempt = UUID.randomUUID();
		List<MatchmakingEntry> reserved = repository.reserveCandidates(GAME, attempt, 2);
		UUID roomId = UUID.randomUUID();

		boolean finalized = repository.finalizeToRoom(reserved, attempt, roomId);

		assertThat(finalized).isTrue();
		assertThat(repository.find(A)).get().satisfies(e -> {
			assertThat(e.matchStatus()).isEqualTo(MatchStatus.ENTERING_ROOM);
			assertThat(e.waitingRoomId()).isEqualTo(roomId);
		});
	}

	@Test
	void finalizeToRoomFailsWhenAttemptNoLongerMatches() {
		enqueuePair();
		UUID attempt = UUID.randomUUID();
		List<MatchmakingEntry> reserved = repository.reserveCandidates(GAME, attempt, 2);
		repository.delete(A);

		boolean finalized = repository.finalizeToRoom(reserved, attempt, UUID.randomUUID());

		assertThat(finalized).isFalse();
		assertThat(repository.find(B)).get()
			.satisfies(e -> assertThat(e.matchStatus()).isEqualTo(MatchStatus.MATCHING));
	}

	@Test
	void reregisterAtCurrentTimeResetsToSearchingWithFreshScore() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));
		UUID attempt = UUID.randomUUID();
		repository.reserveCandidates(GAME, attempt, 1);
		MatchmakingEntry reserved = repository.find(A).orElseThrow();

		boolean requeued = repository.reregisterAtCurrentTime(reserved, attempt);

		assertThat(requeued).isTrue();
		assertThat(repository.find(A)).get().satisfies(e -> {
			assertThat(e.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
			assertThat(e.matchAttemptId()).isNull();
			assertThat(e.waitingRoomId()).isNull();
		});
		Double score = stringRedisTemplate.opsForZSet().score(queueKey(), A);
		assertThat(score).isNotNull();
		assertThat(score).isGreaterThan((double) PAST.toEpochMilli());
	}

	@Test
	void reregisterAtCurrentTimeGuardsStaleAttempt() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));
		UUID attempt = UUID.randomUUID();
		repository.reserveCandidates(GAME, attempt, 1);
		MatchmakingEntry reserved = repository.find(A).orElseThrow();

		boolean requeued = repository.reregisterAtCurrentTime(reserved, UUID.randomUUID());

		assertThat(requeued).isFalse();
		assertThat(repository.find(A)).get()
			.satisfies(e -> assertThat(e.matchStatus()).isEqualTo(MatchStatus.MATCHING));
	}

	private void enqueuePair() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));
		repository.enqueue(MatchmakingEntry.searching(B, GAME, PAST.plusSeconds(1)));
	}

	private String queueKey() {
		return redisKeyBuilder.build("matchmaking", "queue", GAME.name());
	}
}
