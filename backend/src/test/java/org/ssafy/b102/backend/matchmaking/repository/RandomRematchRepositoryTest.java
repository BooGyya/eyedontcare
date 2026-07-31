package org.ssafy.b102.backend.matchmaking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

@DataRedisTest(properties = "app.matchmaking.entry-ttl=PT30M")
@Import({RedisConfig.class, RedisKeyBuilder.class, MatchmakingEntryRepository.class})
class RandomRematchRepositoryTest {

	private static final String PARTICIPANT_KEY = "USER:991";
	private static final GameName GAME_NAME = GameName.HOCKEY;

	@Autowired
	private MatchmakingEntryRepository repository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private RedisKeyBuilder keyBuilder;

	@Test
	void requeuesOnlyOnceWithFreshTimeAndTtl() {
		UUID roomId = UUID.randomUUID();
		Instant oldQueuedAt = Instant.parse("2026-07-01T00:00:00Z");
		Instant requeuedAt = Instant.parse("2026-07-31T03:00:00.123Z");
		MatchmakingEntry entering = MatchmakingEntry.searching(
			PARTICIPANT_KEY,
			GAME_NAME,
			oldQueuedAt
		).enterRoom(roomId, oldQueuedAt.plusSeconds(1));
		repository.save(entering);

		RematchRegistrationResult first =
			repository.requeueRemaining(roomId, GAME_NAME, PARTICIPANT_KEY, requeuedAt);
		RematchRegistrationResult duplicate =
			repository.requeueRemaining(roomId, GAME_NAME, PARTICIPANT_KEY, requeuedAt.plusSeconds(10));

		assertThat(first).isEqualTo(RematchRegistrationResult.REQUEUED);
		assertThat(duplicate).isEqualTo(RematchRegistrationResult.ALREADY_REQUEUED);
		assertThat(repository.find(PARTICIPANT_KEY)).get().satisfies(entry -> {
			assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
			assertThat(entry.queuedAt()).isEqualTo(requeuedAt);
			assertThat(entry.waitingRoomId()).isNull();
			assertThat(entry.matchAttemptId()).isNull();
		});
		assertThat(redisTemplate.opsForZSet().score(queueKey(), PARTICIPANT_KEY))
			.isEqualTo((double) requeuedAt.toEpochMilli());
		assertThat(redisTemplate.opsForZSet().size(queueKey())).isEqualTo(1L);
		assertThat(redisTemplate.getExpire(entryKey()))
			.isBetween(1_740L, 1_800L);
	}

	@Test
	void staleRoomDoesNotOverwriteCurrentEntry() {
		UUID currentRoomId = UUID.randomUUID();
		Instant enteredAt = Instant.parse("2026-07-31T03:00:00Z");
		MatchmakingEntry current = MatchmakingEntry.searching(
			PARTICIPANT_KEY,
			GAME_NAME,
			enteredAt.minusSeconds(30)
		).enterRoom(currentRoomId, enteredAt);
		repository.save(current);

		RematchRegistrationResult result = repository.requeueRemaining(
			UUID.randomUUID(),
			GAME_NAME,
			PARTICIPANT_KEY,
			enteredAt.plusSeconds(1)
		);

		assertThat(result).isEqualTo(RematchRegistrationResult.STALE);
		assertThat(repository.find(PARTICIPANT_KEY)).contains(current);
		assertThat(redisTemplate.opsForZSet().score(queueKey(), PARTICIPANT_KEY)).isNull();
	}

	@Test
	void matchingEntryCannotRegressToSearching() {
		UUID roomId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-31T03:00:00Z");
		MatchmakingEntry matching = MatchmakingEntry.searching(
			PARTICIPANT_KEY,
			GAME_NAME,
			now.minusSeconds(30)
		).reserve(UUID.randomUUID(), now);
		repository.save(matching);

		RematchRegistrationResult result =
			repository.requeueRemaining(roomId, GAME_NAME, PARTICIPANT_KEY, now.plusSeconds(1));

		assertThat(result).isEqualTo(RematchRegistrationResult.STALE);
		assertThat(repository.find(PARTICIPANT_KEY)).contains(matching);
	}

	@Test
	void concurrentDuplicateRequestsCreateOneRegistration() {
		UUID roomId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-31T03:00:00Z");
		MatchmakingEntry entering = MatchmakingEntry.searching(
			PARTICIPANT_KEY,
			GAME_NAME,
			now.minusSeconds(30)
		).enterRoom(roomId, now.minusSeconds(20));
		repository.save(entering);

		CompletableFuture<RematchRegistrationResult> first = CompletableFuture.supplyAsync(() ->
			repository.requeueRemaining(roomId, GAME_NAME, PARTICIPANT_KEY, now));
		CompletableFuture<RematchRegistrationResult> second = CompletableFuture.supplyAsync(() ->
			repository.requeueRemaining(roomId, GAME_NAME, PARTICIPANT_KEY, now.plusMillis(1)));

		assertThat(List.of(first.join(), second.join()))
			.containsExactlyInAnyOrder(
				RematchRegistrationResult.REQUEUED,
				RematchRegistrationResult.ALREADY_REQUEUED
			);
		assertThat(redisTemplate.opsForZSet().size(queueKey())).isEqualTo(1L);
	}

	private String queueKey() {
		return keyBuilder.build("matchmaking", "queue", GAME_NAME.name());
	}

	private String entryKey() {
		return keyBuilder.build("matchmaking", "entry", PARTICIPANT_KEY);
	}
}
