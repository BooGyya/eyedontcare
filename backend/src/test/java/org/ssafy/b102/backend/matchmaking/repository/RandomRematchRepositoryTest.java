package org.ssafy.b102.backend.matchmaking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
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

@DataRedisTest(properties = "app.matchmaking.entry-ttl=PT30M")
@Import({RedisConfig.class, RedisKeyBuilder.class, MatchmakingEntryRepository.class})
class RandomRematchRepositoryTest {

	private static final String PARTICIPANT_KEY = "USER:991";
	private static final List<String> TEST_PARTICIPANT_KEYS = List.of(
		PARTICIPANT_KEY,
		"USER:992",
		"GUEST:00000000-0000-0000-0000-000000000992",
		"USER:993",
		"USER:994",
		"USER:995",
		"USER:996"
	);
	private static final GameName GAME_NAME = GameName.HOCKEY;

	@Autowired
	private MatchmakingEntryRepository repository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private RedisKeyBuilder keyBuilder;

	@BeforeEach
	void setUp() {
		cleanTestParticipants();
	}

	@AfterEach
	void tearDown() {
		cleanTestParticipants();
	}

	private void cleanTestParticipants() {
		for (String participantKey : TEST_PARTICIPANT_KEYS) {
			redisTemplate.delete(entryKey(participantKey));
			redisTemplate.opsForZSet().remove(queueKey(), participantKey);
		}
	}

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
		String rematchToken = entryToken(PARTICIPANT_KEY);
		String markerToken = redisTemplate.opsForValue().get(
			rematchKey(PARTICIPANT_KEY, roomId)
		);
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
		assertThat(rematchToken).isNotBlank().isEqualTo(markerToken);
		assertThat(entryToken(PARTICIPANT_KEY)).isEqualTo(rematchToken);
		assertThat(redisTemplate.opsForValue().get(rematchKey(PARTICIPANT_KEY, roomId)))
			.isEqualTo(rematchToken);
		assertThat(redisTemplate.getExpire(entryKey()))
			.isBetween(1_740L, 1_800L);
		assertThat(redisTemplate.getExpire(rematchKey(PARTICIPANT_KEY, roomId)))
			.isBetween(1_740L, 1_800L);
	}

	@Test
	void cleanupCancelsOnlyIdentifiedUserAndGuestRematchEntries() {
		assertRematchCleanup("USER:992");
		assertRematchCleanup("GUEST:00000000-0000-0000-0000-000000000992");
	}

	@Test
	void cleanupProtectsDirectSearchingEntryFromOldMarker() {
		String participantKey = "USER:993";
		UUID previousRoomId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-31T03:00:00Z");
		repository.save(
			MatchmakingEntry.searching(participantKey, GAME_NAME, now.minusSeconds(30))
				.enterRoom(previousRoomId, now.minusSeconds(20))
		);
		assertThat(repository.requeueRemaining(previousRoomId, GAME_NAME, participantKey, now))
			.isEqualTo(RematchRegistrationResult.REQUEUED);
		String oldMarkerToken = redisTemplate.opsForValue().get(
			rematchKey(participantKey, previousRoomId)
		);

		repository.delete(participantKey);
		assertThat(repository.enqueue(
			MatchmakingEntry.searching(participantKey, GAME_NAME, now.plusSeconds(1))
		)).isTrue();

		assertThat(repository.cleanupRematchAfterPreviousRoomDisconnect(
			participantKey,
			previousRoomId
		)).isEqualTo(RematchCleanupResult.NOT_REMATCH_ENTRY);
		assertThat(repository.find(participantKey)).isPresent();
		assertThat(entryToken(participantKey)).isNull();
		assertThat(redisTemplate.opsForValue().get(rematchKey(participantKey, previousRoomId)))
			.isEqualTo(oldMarkerToken);
	}

	@Test
	void cleanupProtectsEnteringAndInWaitingRoomStates() {
		String participantKey = "USER:994";
		UUID previousRoomId = UUID.randomUUID();
		UUID nextRoomId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-31T03:00:00Z");
		repository.save(
			MatchmakingEntry.searching(participantKey, GAME_NAME, now.minusSeconds(30))
				.enterRoom(previousRoomId, now.minusSeconds(20))
		);
		repository.requeueRemaining(previousRoomId, GAME_NAME, participantKey, now);
		MatchmakingEntry searching = repository.find(participantKey).orElseThrow();
		repository.save(searching.enterRoom(nextRoomId, now.plusSeconds(1)));

		assertThat(repository.cleanupRematchAfterPreviousRoomDisconnect(
			participantKey,
			previousRoomId
		)).isEqualTo(RematchCleanupResult.STATE_CHANGED);
		assertThat(repository.markEntered(participantKey, nextRoomId)).isTrue();
		assertThat(repository.cleanupRematchAfterPreviousRoomDisconnect(
			participantKey,
			previousRoomId
		)).isEqualTo(RematchCleanupResult.STATE_CHANGED);
		assertThat(repository.find(participantKey)).get().satisfies(entry -> {
			assertThat(entry.matchStatus()).isEqualTo(MatchStatus.IN_WAITING_ROOM);
			assertThat(entry.waitingRoomId()).isEqualTo(nextRoomId);
		});
	}

	@Test
	void cleanupProtectsRematchCreatedFromDifferentPreviousRoom() {
		String participantKey = "USER:995";
		UUID oldRoomId = UUID.randomUUID();
		UUID currentRoomId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-31T03:00:00Z");
		repository.save(
			MatchmakingEntry.searching(participantKey, GAME_NAME, now.minusSeconds(30))
				.enterRoom(oldRoomId, now.minusSeconds(20))
		);
		repository.requeueRemaining(oldRoomId, GAME_NAME, participantKey, now);
		MatchmakingEntry firstRematch = repository.find(participantKey).orElseThrow();
		repository.save(firstRematch.enterRoom(currentRoomId, now.plusSeconds(1)));
		repository.requeueRemaining(
			currentRoomId,
			GAME_NAME,
			participantKey,
			now.plusSeconds(2)
		);

		assertThat(repository.cleanupRematchAfterPreviousRoomDisconnect(
			participantKey,
			oldRoomId
		)).isEqualTo(RematchCleanupResult.TOKEN_MISMATCH);
		assertThat(repository.find(participantKey)).get().satisfies(entry -> {
			assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
			assertThat(entry.queuedAt()).isEqualTo(now.plusSeconds(2));
		});
	}

	@Test
	void cleanupProtectsRematchAlreadyPoppedByMatchingWorker() {
		String participantKey = "USER:996";
		UUID previousRoomId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-31T03:00:00Z");
		repository.save(
			MatchmakingEntry.searching(participantKey, GAME_NAME, now.minusSeconds(30))
				.enterRoom(previousRoomId, now.minusSeconds(20))
		);
		repository.requeueRemaining(previousRoomId, GAME_NAME, participantKey, now);
		assertThat(repository.popCandidates(GAME_NAME, 1))
			.singleElement()
			.satisfies(entry -> assertThat(entry.participantKey()).isEqualTo(participantKey));

		assertThat(repository.cleanupRematchAfterPreviousRoomDisconnect(
			participantKey,
			previousRoomId
		)).isEqualTo(RematchCleanupResult.STATE_CHANGED);
		assertThat(repository.find(participantKey)).isPresent();
		assertThat(redisTemplate.hasKey(rematchKey(participantKey, previousRoomId))).isTrue();
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
		MatchmakingEntry registered = repository.find(PARTICIPANT_KEY).orElseThrow();
		assertThat(registered.queuedAt())
			.isIn(now, now.plusMillis(1));
		assertThat(redisTemplate.opsForZSet().score(queueKey(), PARTICIPANT_KEY))
			.isEqualTo(registered.queueScore());
	}

	private String queueKey() {
		return keyBuilder.build("matchmaking", "queue", GAME_NAME.name());
	}

	private String entryKey() {
		return entryKey(PARTICIPANT_KEY);
	}

	private String entryKey(String participantKey) {
		return keyBuilder.build("matchmaking", "entry", participantKey);
	}

	private String rematchKey(String participantKey, UUID previousRoomId) {
		return keyBuilder.build(
			"matchmaking",
			"rematch",
			participantKey,
			previousRoomId.toString()
		);
	}

	private String entryToken(String participantKey) {
		return (String) redisTemplate.opsForHash().get(
			entryKey(participantKey),
			"rematchToken"
		);
	}

	private void assertRematchCleanup(String participantKey) {
		UUID previousRoomId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-31T03:00:00Z");
		repository.save(
			MatchmakingEntry.searching(participantKey, GAME_NAME, now.minusSeconds(30))
				.enterRoom(previousRoomId, now.minusSeconds(20))
		);
		assertThat(repository.requeueRemaining(previousRoomId, GAME_NAME, participantKey, now))
			.isEqualTo(RematchRegistrationResult.REQUEUED);

		assertThat(repository.cleanupRematchAfterPreviousRoomDisconnect(
			participantKey,
			previousRoomId
		)).isEqualTo(RematchCleanupResult.CANCELLED);
		assertThat(repository.find(participantKey)).isEmpty();
		assertThat(redisTemplate.opsForZSet().score(queueKey(), participantKey)).isNull();
		assertThat(redisTemplate.hasKey(rematchKey(participantKey, previousRoomId))).isFalse();
		assertThat(repository.cleanupRematchAfterPreviousRoomDisconnect(
			participantKey,
			previousRoomId
		)).isEqualTo(RematchCleanupResult.NOT_FOUND);
	}
}
