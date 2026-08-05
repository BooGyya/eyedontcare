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
	private static final String GUEST = "GUEST:550e8400-e29b-41d4-a716-446655440000";
	private static final Instant PAST = Instant.parse("2026-07-01T00:00:00Z");

	@Autowired
	private MatchmakingEntryRepository repository;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private RedisKeyBuilder redisKeyBuilder;

	@BeforeEach
	void setUp() {
		List.of(A, B, GUEST).forEach(repository::delete);
		for (GameName game : GameName.values()) {
			stringRedisTemplate.delete(redisKeyBuilder.build("matchmaking", "queue", game.name()));
		}
	}

	@Test
	void enqueueRepairsQueueOnlyGhostAndCreatesEntryAtomically() {
		stringRedisTemplate.opsForZSet().add(queueKey(), A, PAST.toEpochMilli());

		boolean created = repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));

		assertThat(created).isTrue();
		assertThat(repository.find(A)).isPresent();
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey())).isEqualTo(1L);
	}

	@Test
	void enqueueRejectsExistingEntryWithoutLeavingQueueGhost() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));
		stringRedisTemplate.opsForZSet().remove(queueKey(), A);

		boolean created = repository.enqueue(
			MatchmakingEntry.searching(A, GAME, PAST.plusSeconds(1))
		);

		assertThat(created).isFalse();
		assertThat(stringRedisTemplate.opsForZSet().score(queueKey(), A)).isNull();
		assertThat(repository.find(A)).get()
			.satisfies(entry -> assertThat(entry.queuedAt()).isEqualTo(PAST));
	}

	@Test
	void enqueueUsesSameAtomicRegistrationForGuestParticipant() {
		boolean created = repository.enqueue(
			MatchmakingEntry.searching(GUEST, GAME, PAST)
		);

		assertThat(created).isTrue();
		assertThat(repository.find(GUEST)).isPresent();
		assertThat(stringRedisTemplate.opsForZSet().score(queueKey(), GUEST))
			.isEqualTo((double) PAST.toEpochMilli());
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

	@Test
	void staleSearchingHashWithoutQueueIsRemovedAtomically() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));
		stringRedisTemplate.opsForZSet().remove(queueKey(), A);

		boolean removed = repository.deleteStaleSearchingIfQueueMissing(A, GAME);

		assertThat(removed).isTrue();
		assertThat(repository.find(A)).isEmpty();
	}

	@Test
	void queuedSearchingEntryIsProtectedFromStaleCleanup() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));

		boolean removed = repository.deleteStaleSearchingIfQueueMissing(A, GAME);

		assertThat(removed).isFalse();
		assertThat(repository.find(A)).isPresent();
	}

	@Test
	void compareDeleteRemovesMatchingRoomAndQueueMember() {
		UUID roomId = UUID.randomUUID();
		repository.save(new MatchmakingEntry(
			A,
			GAME,
			MatchStatus.IN_WAITING_ROOM,
			roomId,
			PAST,
			PAST,
			UUID.randomUUID()
		));
		stringRedisTemplate.opsForZSet().add(queueKey(), A, PAST.toEpochMilli());

		MatchmakingEntryRepository.EntryDeleteResult result =
			repository.deleteIfRoomMatches(A, roomId);

		assertThat(result)
			.isEqualTo(MatchmakingEntryRepository.EntryDeleteResult.DELETED);
		assertThat(repository.find(A)).isEmpty();
		assertThat(stringRedisTemplate.opsForZSet().score(queueKey(), A)).isNull();
	}

	@Test
	void compareDeleteProtectsDifferentRoomEntry() {
		UUID currentRoomId = UUID.randomUUID();
		repository.save(new MatchmakingEntry(
			A,
			GAME,
			MatchStatus.ENTERING_ROOM,
			currentRoomId,
			PAST,
			PAST,
			UUID.randomUUID()
		));

		MatchmakingEntryRepository.EntryDeleteResult result =
			repository.deleteIfRoomMatches(A, UUID.randomUUID());

		assertThat(result)
			.isEqualTo(MatchmakingEntryRepository.EntryDeleteResult.ROOM_MISMATCH);
		assertThat(repository.find(A)).isPresent();
	}

	@Test
	void compareDeleteOfMissingEntryIsIdempotentNoOp() {
		stringRedisTemplate.opsForZSet().add(queueKey(), A, PAST.toEpochMilli());

		MatchmakingEntryRepository.EntryDeleteResult result =
			repository.deleteIfRoomMatches(A, UUID.randomUUID());

		assertThat(result)
			.isEqualTo(MatchmakingEntryRepository.EntryDeleteResult.NOT_FOUND);
		assertThat(stringRedisTemplate.opsForZSet().score(queueKey(), A)).isNull();
	}

	private void enqueuePair() {
		repository.enqueue(MatchmakingEntry.searching(A, GAME, PAST));
		repository.enqueue(MatchmakingEntry.searching(B, GAME, PAST.plusSeconds(1)));
	}

	private String queueKey() {
		return redisKeyBuilder.build("matchmaking", "queue", GAME.name());
	}
}
