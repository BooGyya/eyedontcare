package org.ssafy.b102.backend.matchmaking.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.config.RedisConfig;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.entity.MatchmakingEntry;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository;

/**
 * WaitingRoom lifecycle 콜백이 Matchmaking entry에 어떻게 반영되는지 실제 Redis로 검증한다.
 * roomId 비교로 stale 콜백이 다른 매칭 entry를 건드리지 못하는 것이 핵심이다.
 */
@DataRedisTest
@Import({RedisConfig.class, RedisKeyBuilder.class, MatchmakingEntryRepository.class})
class MatchmakingRandomRoomLifecycleAdapterTest {

	private static final GameName GAME = GameName.HOCKEY;
	private static final String KEY = "USER:1";
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	@Autowired
	private MatchmakingEntryRepository repository;

	private MatchmakingRandomRoomLifecycleAdapter adapter;

	@BeforeEach
	void setUp() {
		repository.delete(KEY);
		adapter = new MatchmakingRandomRoomLifecycleAdapter(repository);
	}

	@Test
	void markParticipantEnteredMovesEnteringRoomToInWaitingRoom() {
		UUID roomId = UUID.randomUUID();
		repository.save(entry(MatchStatus.ENTERING_ROOM, roomId));

		adapter.markParticipantEntered(roomId, KEY);

		assertThat(repository.find(KEY)).get()
			.satisfies(e -> assertThat(e.matchStatus()).isEqualTo(MatchStatus.IN_WAITING_ROOM));
	}

	@Test
	void markParticipantEnteredIsIdempotent() {
		UUID roomId = UUID.randomUUID();
		repository.save(entry(MatchStatus.ENTERING_ROOM, roomId));

		adapter.markParticipantEntered(roomId, KEY);
		adapter.markParticipantEntered(roomId, KEY);

		assertThat(repository.find(KEY)).get()
			.satisfies(e -> assertThat(e.matchStatus()).isEqualTo(MatchStatus.IN_WAITING_ROOM));
	}

	@Test
	void markParticipantEnteredIgnoresDifferentRoomId() {
		UUID roomId = UUID.randomUUID();
		repository.save(entry(MatchStatus.ENTERING_ROOM, roomId));

		adapter.markParticipantEntered(UUID.randomUUID(), KEY);

		assertThat(repository.find(KEY)).get()
			.satisfies(e -> assertThat(e.matchStatus()).isEqualTo(MatchStatus.ENTERING_ROOM));
	}

	@Test
	void completeRandomRoomDeletesEntryOfSameRoom() {
		UUID roomId = UUID.randomUUID();
		repository.save(entry(MatchStatus.IN_WAITING_ROOM, roomId));

		adapter.completeRandomRoom(roomId, List.of(KEY));

		assertThat(repository.find(KEY)).isEmpty();
	}

	@Test
	void completeRandomRoomKeepsEntryOfDifferentRoom() {
		UUID roomId = UUID.randomUUID();
		repository.save(entry(MatchStatus.IN_WAITING_ROOM, roomId));

		adapter.completeRandomRoom(UUID.randomUUID(), List.of(KEY));

		assertThat(repository.find(KEY)).isPresent();
	}

	@Test
	void failedEntryCleanupDeletesOnlyEnteringRoomEntry() {
		UUID roomId = UUID.randomUUID();
		repository.save(entry(MatchStatus.ENTERING_ROOM, roomId));

		adapter.cleanupFailedParticipant(roomId, KEY);

		assertThat(repository.find(KEY)).isEmpty();
	}

	@Test
	void failedEntryCleanupProtectsDifferentRoomEntry() {
		UUID currentRoomId = UUID.randomUUID();
		repository.save(entry(MatchStatus.ENTERING_ROOM, currentRoomId));

		adapter.cleanupFailedParticipant(UUID.randomUUID(), KEY);

		assertThat(repository.find(KEY)).isPresent();
	}

	private static MatchmakingEntry entry(MatchStatus status, UUID roomId) {
		return new MatchmakingEntry(KEY, GAME, status, roomId, NOW, NOW, UUID.randomUUID());
	}
}
