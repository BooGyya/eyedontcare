package org.ssafy.b102.backend.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.config.RedisConfig;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.matchmaking.dto.response.MatchStatusResponse;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.entity.MatchmakingEntry;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.user.repository.UserRepository;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomService;
import org.ssafy.b102.testfixture.websocket.RecordingMatchNotifier;

@DataRedisTest
@Import({
	RedisConfig.class,
	RedisKeyBuilder.class,
	MatchmakingEntryRepository.class,
	MatchmakingService.class,
	RecordingMatchNotifier.class
})
class MatchmakingStaleEntryServiceTest {

	private static final String PARTICIPANT_KEY = "USER:101";
	private static final String GUEST_KEY = "GUEST:550e8400-e29b-41d4-a716-446655440000";
	private static final GameName GAME = GameName.HOCKEY;
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private GuestSessionService guestSessionService;

	@MockitoBean
	private WaitingRoomService waitingRoomService;

	@org.springframework.beans.factory.annotation.Autowired
	private MatchmakingService matchmakingService;

	@org.springframework.beans.factory.annotation.Autowired
	private MatchmakingEntryRepository repository;

	@org.springframework.beans.factory.annotation.Autowired
	private StringRedisTemplate redisTemplate;

	@org.springframework.beans.factory.annotation.Autowired
	private RedisKeyBuilder redisKeyBuilder;

	@BeforeEach
	void setUp() {
		when(userRepository.existsByIdAndDeletedAtIsNull(any())).thenReturn(true);
		when(guestSessionService.exists(any())).thenReturn(true);
		List.of(PARTICIPANT_KEY, GUEST_KEY).forEach(repository::delete);
		for (GameName game : GameName.values()) {
			redisTemplate.delete(redisKeyBuilder.build("matchmaking", "queue", game.name()));
		}
	}

	@Test
	void searchingHashWithoutQueueIsRecoveredAndJoinRetriesOnce() {
		repository.enqueue(MatchmakingEntry.searching(PARTICIPANT_KEY, GAME, NOW));
		redisTemplate.opsForZSet().remove(queueKey(), PARTICIPANT_KEY);

		MatchStatusResponse response = matchmakingService.join(PARTICIPANT_KEY, GAME.name());

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(repository.find(PARTICIPANT_KEY)).get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
		assertThat(redisTemplate.opsForZSet().score(queueKey(), PARTICIPANT_KEY)).isNotNull();
	}

	@Test
	void guestSearchingHashWithoutQueueUsesTheSameRecoveryPolicy() {
		repository.enqueue(MatchmakingEntry.searching(GUEST_KEY, GAME, NOW));
		redisTemplate.opsForZSet().remove(queueKey(), GUEST_KEY);

		MatchStatusResponse response = matchmakingService.join(GUEST_KEY, GAME.name());

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(repository.find(GUEST_KEY)).isPresent();
	}

	@Test
	void liveEnteringRoomEntryRemainsAlreadyInQueue() {
		UUID roomId = UUID.randomUUID();
		repository.save(enteringEntry(roomId));
		when(waitingRoomService.findSnapshot(roomId)).thenReturn(snapshot(roomId, RoomStatus.WAITING));

		assertThatThrownBy(() -> matchmakingService.join(PARTICIPANT_KEY, GAME.name()))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
				.isEqualTo(MatchmakingErrorCode.ALREADY_IN_QUEUE));
		assertThat(repository.find(PARTICIPANT_KEY)).isPresent();
	}

	@Test
	void activeWaitingRoomWithoutParticipantIsCleanedAndJoinRetries() {
		UUID roomId = UUID.randomUUID();
		repository.save(enteringEntry(roomId));
		when(waitingRoomService.findSnapshot(roomId))
			.thenReturn(snapshotWithoutParticipant(roomId, RoomStatus.WAITING));

		MatchStatusResponse response = matchmakingService.join(PARTICIPANT_KEY, GAME.name());

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(repository.find(PARTICIPANT_KEY)).get()
			.satisfies(entry -> {
				assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
				assertThat(entry.waitingRoomId()).isNull();
			});
		assertThat(redisTemplate.opsForZSet().score(queueKey(), PARTICIPANT_KEY)).isNotNull();
	}

	@Test
	void activeCountdownRoomWithoutParticipantIsCleanedAndJoinRetries() {
		UUID roomId = UUID.randomUUID();
		repository.save(new MatchmakingEntry(
			PARTICIPANT_KEY,
			GAME,
			MatchStatus.IN_WAITING_ROOM,
			roomId,
			NOW,
			NOW,
			UUID.randomUUID()
		));
		when(waitingRoomService.findSnapshot(roomId))
			.thenReturn(snapshotWithoutParticipant(roomId, RoomStatus.COUNTDOWN));

		MatchStatusResponse response = matchmakingService.join(PARTICIPANT_KEY, GAME.name());

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(repository.find(PARTICIPANT_KEY)).get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
	}

	@Test
	void guestActiveWaitingRoomWithoutParticipantUsesTheSameStaleRecovery() {
		UUID roomId = UUID.randomUUID();
		repository.save(new MatchmakingEntry(
			GUEST_KEY,
			GAME,
			MatchStatus.ENTERING_ROOM,
			roomId,
			NOW,
			NOW,
			UUID.randomUUID()
		));
		when(waitingRoomService.findSnapshot(roomId))
			.thenReturn(snapshotWithoutParticipant(roomId, RoomStatus.WAITING));

		MatchStatusResponse response = matchmakingService.join(GUEST_KEY, GAME.name());

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(repository.find(GUEST_KEY)).get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
	}

	@Test
	void missingEnteringRoomIsCleanedAndJoinRetries() {
		UUID roomId = UUID.randomUUID();
		repository.save(enteringEntry(roomId));
		when(waitingRoomService.findSnapshot(roomId)).thenThrow(
			new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND)
		);

		MatchStatusResponse response = matchmakingService.join(PARTICIPANT_KEY, GAME.name());

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(repository.find(PARTICIPANT_KEY)).get()
			.satisfies(entry -> assertThat(entry.waitingRoomId()).isNull());
	}

	@Test
	void closedWaitingRoomForInWaitingEntryIsCleanedAndJoinRetries() {
		UUID roomId = UUID.randomUUID();
		repository.save(new MatchmakingEntry(
			PARTICIPANT_KEY,
			GAME,
			MatchStatus.IN_WAITING_ROOM,
			roomId,
			NOW,
			NOW,
			UUID.randomUUID()
		));
		when(waitingRoomService.findSnapshot(roomId)).thenReturn(snapshot(roomId, RoomStatus.CLOSED));

		MatchStatusResponse response = matchmakingService.join(PARTICIPANT_KEY, GAME.name());

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(repository.find(PARTICIPANT_KEY)).get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
	}

	@Test
	void closedWaitingRoomIsCleanedWithoutDeletingNewRoomEntry() {
		UUID oldRoomId = UUID.randomUUID();
		UUID newRoomId = UUID.randomUUID();
		repository.save(enteringEntry(oldRoomId));
		when(waitingRoomService.findSnapshot(oldRoomId)).thenAnswer(invocation -> {
			repository.save(enteringEntry(newRoomId));
			return snapshot(oldRoomId, RoomStatus.CLOSED);
		});

		assertThatThrownBy(() -> matchmakingService.join(PARTICIPANT_KEY, GAME.name()))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
				.isEqualTo(MatchmakingErrorCode.ALREADY_IN_QUEUE));
		assertThat(repository.find(PARTICIPANT_KEY)).get()
			.satisfies(entry -> assertThat(entry.waitingRoomId()).isEqualTo(newRoomId));
	}

	@Test
	void waitingRoomStoreFailureDoesNotDeleteEntryAsStale() {
		UUID roomId = UUID.randomUUID();
		repository.save(enteringEntry(roomId));
		when(waitingRoomService.findSnapshot(roomId)).thenThrow(
			new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE)
		);

		assertThatThrownBy(() -> matchmakingService.join(PARTICIPANT_KEY, GAME.name()))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
				.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));
		assertThat(repository.find(PARTICIPANT_KEY)).isPresent();
	}

	private MatchmakingEntry enteringEntry(UUID roomId) {
		return new MatchmakingEntry(
			PARTICIPANT_KEY,
			GAME,
			MatchStatus.ENTERING_ROOM,
			roomId,
			NOW,
			NOW,
			UUID.randomUUID()
		);
	}

	private WaitingRoomSnapshot snapshot(UUID roomId, RoomStatus status) {
		return new WaitingRoomSnapshot(
			new WaitingRoom(roomId, RoomType.RANDOM, GAME, null, status, NOW),
			List.of(
				participant(PARTICIPANT_KEY, 1),
				participant("USER:102", 2)
			)
		);
	}

	private WaitingRoomSnapshot snapshotWithoutParticipant(UUID roomId, RoomStatus status) {
		WaitingRoom room = status == RoomStatus.COUNTDOWN
			? new WaitingRoom(
				roomId,
				RoomType.RANDOM,
				GAME,
				null,
				status,
				NOW,
				UUID.randomUUID(),
				NOW.plusSeconds(3)
			)
			: new WaitingRoom(roomId, RoomType.RANDOM, GAME, null, status, NOW);
		return new WaitingRoomSnapshot(
			room,
			List.of(
				participant("USER:1", 1),
				participant("USER:2", 2)
			)
		);
	}

	private WaitingRoomParticipant participant(String key, int slot) {
		return new WaitingRoomParticipant(
			key,
			key,
			RoomRole.PLAYER,
			slot,
			false,
			CalibrationStatus.PENDING,
			NOW
		);
	}

	private String queueKey() {
		return redisKeyBuilder.build("matchmaking", "queue", GAME.name());
	}
}
