package org.ssafy.b102.backend.waitingroom.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.config.RedisConfig;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import tools.jackson.databind.json.JsonMapper;

@DataRedisTest
@Import({
	RedisConfig.class,
	RedisWaitingRoomStore.class,
	RedisWaitingRoomStoreIntegrationTest.TestConfig.class
})
class RedisWaitingRoomStoreIntegrationTest {

	private static final UUID ROOM_ID =
		UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f");
	private static final Duration TTL = Duration.ofMinutes(10);
	private static final String ROOM_KEY =
		"edc:test:waiting-room:room:c93c76b2-7f78-4275-b8af-7cdd921bbb4f";
	private static final String PARTICIPANTS_KEY =
		"edc:test:waiting-room:participants:c93c76b2-7f78-4275-b8af-7cdd921bbb4f";
	private static final String INVITE_CODE_KEY =
		"edc:test:waiting-room:invite-code:0123";

	@Autowired
	private RedisWaitingRoomStore store;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@AfterEach
	void tearDown() {
		redisTemplate.delete(List.of(ROOM_KEY, PARTICIPANTS_KEY, INVITE_CODE_KEY));
	}

	@Test
	void luaCreatesRoomParticipantAndInviteIndexWithSameTtl() {
		CreateInviteRoomResult result = store.createInviteRoomAtomically(command());

		assertThat(result).isEqualTo(CreateInviteRoomResult.CREATED);
		assertThat(redisTemplate.opsForHash().entries(ROOM_KEY))
			.containsEntry("roomType", "INVITE")
			.containsEntry("gameName", "EYEFIGHT")
			.containsEntry("roomCode", "0123")
			.containsEntry("roomStatus", "WAITING")
			.containsEntry("createdAt", "2026-07-30T04:00:00Z");
		assertThat(redisTemplate.opsForValue().get(INVITE_CODE_KEY))
			.isEqualTo(ROOM_ID.toString());
		String participantJson = (String) redisTemplate.opsForHash()
			.get(PARTICIPANTS_KEY, "USER:1");
		assertThat(participantJson)
			.contains("\"displayName\":\"회원닉네임\"")
			.contains("\"roomRole\":\"HOST\"")
			.contains("\"isReady\":false")
			.doesNotContain("participantKey");

		List<Long> ttls = List.of(ROOM_KEY, PARTICIPANTS_KEY, INVITE_CODE_KEY)
			.stream()
			.map(key -> redisTemplate.getExpire(key, TimeUnit.SECONDS))
			.toList();
		assertThat(ttls).allSatisfy(ttl -> assertThat(ttl).isBetween(590L, 600L));
		assertThat(ttls.stream().mapToLong(Long::longValue).max().orElseThrow()
			- ttls.stream().mapToLong(Long::longValue).min().orElseThrow())
			.isLessThanOrEqualTo(1L);
	}

	@Test
	void randomRoomStartsCountdownWhenSecondPlayerBecomesReady() {
		Instant createdAt = Instant.parse("2026-07-30T04:00:00Z");
		WaitingRoom room = new WaitingRoom(
			ROOM_ID,
			RoomType.RANDOM,
			GameName.EYEFIGHT,
			null,
			RoomStatus.WAITING,
			createdAt
		);
		List<WaitingRoomParticipant> participants = List.of(
			randomParticipant("USER:1", 1, createdAt),
			randomParticipant("GUEST:00000000-0000-0000-0000-000000000002", 2, createdAt)
		);

		assertThat(
			store.createRandomRoomAtomically(
				new CreateRandomRoomCommand(room, participants, TTL)
			)
		).isTrue();
		assertThat(redisTemplate.opsForHash().get(ROOM_KEY, "roomCode")).isNull();
		assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isFalse();

		for (WaitingRoomParticipant participant : participants) {
			completeRandomCalibration(participant.participantKey());
		}
		UUID firstCandidate = UUID.randomUUID();
		assertThat(
			store.updateRandomReadyAtomically(
				new UpdateReadyCommand(ROOM_ID, null, "USER:1", true, 2, TTL),
				firstCandidate,
				createdAt.plusSeconds(3)
			).status()
		).isEqualTo(RandomReadyResult.Status.UPDATED);

		UUID countdownId = UUID.randomUUID();
		Instant countdownEndsAt = createdAt.plusSeconds(4);
		RandomReadyResult second = store.updateRandomReadyAtomically(
			new UpdateReadyCommand(
				ROOM_ID,
				null,
				"GUEST:00000000-0000-0000-0000-000000000002",
				true,
				2,
				TTL
			),
			countdownId,
			countdownEndsAt
		);

		assertThat(second.status())
			.isEqualTo(RandomReadyResult.Status.COUNTDOWN_STARTED);
		assertThat(second.countdownId()).isEqualTo(countdownId);
		assertThat(store.findSnapshot(ROOM_ID).orElseThrow().room().roomStatus())
			.isEqualTo(RoomStatus.COUNTDOWN);
		assertThat(
			store.completeCountdownAtomically(
				new CompleteCountdownCommand(
					ROOM_ID,
					null,
					countdownId,
					countdownEndsAt,
					2,
					TTL
				)
			)
		).isEqualTo(CompleteCountdownResult.STARTED);
		assertThat(store.findSnapshot(ROOM_ID).orElseThrow().room().roomStatus())
			.isEqualTo(RoomStatus.IN_GAME);
	}

	@Test
	void randomLeaveClosesRoomAndPreservesBothParticipants() {
		createRandomRoom();

		RandomRoomLeaveResult result = store.leaveRandomRoomAtomically(
			new LeaveRandomRoomCommand(ROOM_ID, "USER:1", Duration.ofSeconds(30))
		);

		assertThat(result.status())
			.isEqualTo(RandomRoomLeaveResult.Status.CLOSED_NOW);
		assertThat(result.quitterParticipantKey()).isEqualTo("USER:1");
		assertThat(result.remainingParticipantKey())
			.isEqualTo("GUEST:00000000-0000-0000-0000-000000000002");
		assertThat(result.previousRoomStatus()).isEqualTo(RoomStatus.WAITING);
		WaitingRoomSnapshot snapshot = store.findSnapshot(ROOM_ID).orElseThrow();
		assertThat(snapshot.room().roomStatus()).isEqualTo(RoomStatus.CLOSED);
		assertThat(snapshot.room().roomCode()).isNull();
		assertThat(snapshot.participants()).hasSize(2);
		assertThat(redisTemplate.getExpire(ROOM_KEY, TimeUnit.SECONDS))
			.isBetween(25L, 30L);
		assertThat(redisTemplate.getExpire(PARTICIPANTS_KEY, TimeUnit.SECONDS))
			.isBetween(25L, 30L);

		Long ttlBefore = redisTemplate.getExpire(ROOM_KEY, TimeUnit.MILLISECONDS);
		assertThat(
			store.leaveRandomRoomAtomically(
				new LeaveRandomRoomCommand(
					ROOM_ID,
					"USER:1",
					Duration.ofSeconds(30)
				)
			).status()
		).isEqualTo(RandomRoomLeaveResult.Status.ALREADY_CLOSED);
		assertThat(redisTemplate.getExpire(ROOM_KEY, TimeUnit.MILLISECONDS))
			.isLessThanOrEqualTo(ttlBefore);
	}

	@Test
	void inviteCodeConflictDoesNotCreateRoomOrParticipant() {
		redisTemplate.opsForValue().set(INVITE_CODE_KEY, "other-room", TTL);

		CreateInviteRoomResult result = store.createInviteRoomAtomically(command());

		assertThat(result).isEqualTo(CreateInviteRoomResult.INVITE_CODE_CONFLICT);
		assertThat(redisTemplate.hasKey(ROOM_KEY)).isFalse();
		assertThat(redisTemplate.hasKey(PARTICIPANTS_KEY)).isFalse();
		assertThat(redisTemplate.opsForValue().get(INVITE_CODE_KEY)).isEqualTo("other-room");
	}

	@Test
	void findsRoomIdAndAtomicallyJoinsPlayerWithSnapshotAndRenewedTtl() {
		store.createInviteRoomAtomically(command());
		redisTemplate.expire(ROOM_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(PARTICIPANTS_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(INVITE_CODE_KEY, Duration.ofMinutes(5));

		assertThat(store.findRoomIdByInviteCode("0123")).contains(ROOM_ID);
		JoinInviteRoomResult result =
			store.joinInviteRoomAtomically(joinCommand("GUEST:one"));

		assertThat(result.status()).isEqualTo(JoinInviteRoomResult.Status.JOINED);
		assertThat(result.snapshot().participants())
			.extracting(participant -> participant.slotNo())
			.containsExactlyInAnyOrder(1, 2);
		assertThat(result.snapshot().participants())
			.filteredOn(participant -> participant.participantKey().equals("GUEST:one"))
			.singleElement()
			.satisfies(participant -> {
				assertThat(participant.roomRole()).isEqualTo(RoomRole.PLAYER);
				assertThat(participant.isReady()).isFalse();
				assertThat(participant.calibrationStatus())
					.isEqualTo(CalibrationStatus.PENDING);
			});
		assertThat(redisTemplate.opsForHash().get(PARTICIPANTS_KEY, "GUEST:one"))
			.asString()
			.contains("\"slotNo\":2")
			.doesNotContain("participantKey");

		assertRenewedTtls();
	}

	@Test
	void readsCompleteSnapshotWithoutRenewingTtl() {
		setupFullRoom();
		redisTemplate.expire(ROOM_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(PARTICIPANTS_KEY, Duration.ofMinutes(5));
		Long roomTtlBefore = redisTemplate.getExpire(ROOM_KEY, TimeUnit.SECONDS);
		Long participantsTtlBefore =
			redisTemplate.getExpire(PARTICIPANTS_KEY, TimeUnit.SECONDS);

		WaitingRoomSnapshot snapshot = store.findSnapshot(ROOM_ID).orElseThrow();

		assertThat(snapshot.room().roomId()).isEqualTo(ROOM_ID);
		assertThat(snapshot.room().roomStatus()).isEqualTo(RoomStatus.WAITING);
		assertThat(snapshot.participants())
			.extracting(WaitingRoomParticipant::participantKey)
			.containsExactlyInAnyOrder("USER:1", "GUEST:one");
		assertThat(redisTemplate.getExpire(ROOM_KEY, TimeUnit.SECONDS))
			.isBetween(roomTtlBefore - 1, roomTtlBefore);
		assertThat(redisTemplate.getExpire(PARTICIPANTS_KEY, TimeUnit.SECONDS))
			.isBetween(participantsTtlBefore - 1, participantsTtlBefore);
	}

	@Test
	void missingSnapshotReturnsEmptyAndCorruptedSnapshotFails() {
		assertThat(store.findSnapshot(ROOM_ID)).isEmpty();

		store.createInviteRoomAtomically(command());
		redisTemplate.delete(PARTICIPANTS_KEY);

		assertThatThrownBy(() -> store.findSnapshot(ROOM_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(
						WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
					));
	}

	@Test
	void malformedSnapshotParticipantFailsWithoutChangingTtl() {
		store.createInviteRoomAtomically(command());
		redisTemplate.opsForHash().put(
			PARTICIPANTS_KEY,
			"USER:1",
			"not-json"
		);
		Long roomTtlBefore = redisTemplate.getExpire(ROOM_KEY, TimeUnit.SECONDS);

		assertThatThrownBy(() -> store.findSnapshot(ROOM_ID))
			.isInstanceOf(BusinessException.class);
		assertThat(redisTemplate.getExpire(ROOM_KEY, TimeUnit.SECONDS))
			.isBetween(roomTtlBefore - 1, roomTtlBefore);
	}

	@Test
	void duplicateAndFullFailuresDoNotChangeParticipantsOrRenewTtl() {
		store.createInviteRoomAtomically(command());
		store.joinInviteRoomAtomically(joinCommand("GUEST:one"));
		redisTemplate.expire(ROOM_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(PARTICIPANTS_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(INVITE_CODE_KEY, Duration.ofMinutes(5));

		JoinInviteRoomResult duplicate =
			store.joinInviteRoomAtomically(joinCommand("GUEST:one"));
		JoinInviteRoomResult full =
			store.joinInviteRoomAtomically(joinCommand("GUEST:two"));

		assertThat(duplicate.status())
			.isEqualTo(JoinInviteRoomResult.Status.ALREADY_JOINED);
		assertThat(full.status()).isEqualTo(JoinInviteRoomResult.Status.FULL);
		assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY)).isEqualTo(2);
		assertThat(redisTemplate.getExpire(ROOM_KEY, TimeUnit.SECONDS))
			.isBetween(290L, 300L);
		assertThat(redisTemplate.getExpire(PARTICIPANTS_KEY, TimeUnit.SECONDS))
			.isBetween(290L, 300L);
		assertThat(redisTemplate.getExpire(INVITE_CODE_KEY, TimeUnit.SECONDS))
			.isBetween(290L, 300L);
	}

	@Test
	void roomStateAndCorruptedDataAreMappedWithoutMutation() {
		store.createInviteRoomAtomically(command());
		redisTemplate.opsForHash().put(ROOM_KEY, "roomStatus", "COUNTDOWN");
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.NOT_JOINABLE);

		redisTemplate.opsForHash().put(ROOM_KEY, "roomStatus", "WAITING");
		redisTemplate.opsForHash().put(PARTICIPANTS_KEY, "USER:1", "not-json");
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.CORRUPTED);
		assertThat(redisTemplate.opsForHash().hasKey(PARTICIPANTS_KEY, "GUEST:one"))
			.isFalse();
	}

	@Test
	void missingRoomIsInvalidCodeAndMissingParticipantsIsCorrupted() {
		store.createInviteRoomAtomically(command());
		redisTemplate.delete(ROOM_KEY);
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.INVALID_INVITE_CODE);

		tearDown();
		store.createInviteRoomAtomically(command());
		redisTemplate.delete(PARTICIPANTS_KEY);
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.CORRUPTED);
	}

	@Test
	void indexMismatchRandomRoomAndClosedRoomAreRejected() {
		store.createInviteRoomAtomically(command());
		redisTemplate.opsForValue().set(INVITE_CODE_KEY, UUID.randomUUID().toString(), TTL);
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.CORRUPTED);

		redisTemplate.opsForValue().set(INVITE_CODE_KEY, ROOM_ID.toString(), TTL);
		redisTemplate.opsForHash().put(ROOM_KEY, "roomType", "RANDOM");
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.CORRUPTED);

		redisTemplate.opsForHash().put(ROOM_KEY, "roomType", "INVITE");
		redisTemplate.opsForHash().put(ROOM_KEY, "roomStatus", "CLOSED");
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.NOT_JOINABLE);
	}

	@Test
	void invalidStoredSlotsAreCorrupted() {
		store.createInviteRoomAtomically(command());
		assertCorruptedParticipantJson("""
			{"displayName":"host","roomRole":"HOST","isReady":false,
			"calibrationStatus":"PENDING","joinedAt":"2026-07-30T04:00:00Z"}
			""");
		assertCorruptedParticipantJson("""
			{"displayName":"host","roomRole":"HOST","slotNo":3,"isReady":false,
			"calibrationStatus":"PENDING","joinedAt":"2026-07-30T04:00:00Z"}
			""");

		redisTemplate.opsForHash().put(PARTICIPANTS_KEY, "USER:1", """
			{"displayName":"host","roomRole":"HOST","slotNo":1,"isReady":false,
			"calibrationStatus":"PENDING","joinedAt":"2026-07-30T04:00:00Z"}
			""");
		redisTemplate.opsForHash().put(PARTICIPANTS_KEY, "USER:2", """
			{"displayName":"player","roomRole":"PLAYER","slotNo":1,"isReady":false,
			"calibrationStatus":"PENDING","joinedAt":"2026-07-30T04:00:00Z"}
			""");
		assertThat(store.joinInviteRoomAtomically(joinCommandWithCapacity("GUEST:one", 3)).status())
			.isEqualTo(JoinInviteRoomResult.Status.CORRUPTED);
	}

	@Test
	void assignsLowestAvailableSlotInsteadOfHashCountPlusOne() {
		store.createInviteRoomAtomically(command());
		redisTemplate.opsForHash().put(PARTICIPANTS_KEY, "USER:3", """
			{"displayName":"third","roomRole":"PLAYER","slotNo":3,"isReady":false,
			"calibrationStatus":"PENDING","joinedAt":"2026-07-30T04:00:00Z"}
			""");

		JoinInviteRoomResult result =
			store.joinInviteRoomAtomically(joinCommandWithCapacity("GUEST:one", 3));

		assertThat(result.status()).isEqualTo(JoinInviteRoomResult.Status.JOINED);
		assertThat(result.snapshot().participants())
			.filteredOn(participant -> participant.participantKey().equals("GUEST:one"))
			.extracting(participant -> participant.slotNo())
			.containsExactly(2);
	}

	@Test
	void malformedInviteIndexIsStoreUnavailable() {
		redisTemplate.opsForValue().set(INVITE_CODE_KEY, "not-a-uuid", TTL);

		assertThat(org.assertj.core.api.Assertions.catchThrowable(
			() -> store.findRoomIdByInviteCode("0123")
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
			assertThat(exception.getErrorCode())
				.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));
	}

	@Test
	void concurrentJoinsAllowExactlyOnePlayer() throws Exception {
		store.createInviteRoomAtomically(command());
		ExecutorService executor = Executors.newFixedThreadPool(5);
		try {
			List<Callable<JoinInviteRoomResult>> tasks = new ArrayList<>();
			for (int index = 0; index < 5; index++) {
				String participantKey = "GUEST:concurrent-" + index;
				tasks.add(() -> store.joinInviteRoomAtomically(joinCommand(participantKey)));
			}

			List<Future<JoinInviteRoomResult>> futures = executor.invokeAll(tasks);
			List<JoinInviteRoomResult.Status> statuses = new ArrayList<>();
			for (Future<JoinInviteRoomResult> future : futures) {
				statuses.add(future.get().status());
			}

			assertThat(statuses)
				.filteredOn(status -> status == JoinInviteRoomResult.Status.JOINED)
				.hasSize(1);
			assertThat(statuses)
				.filteredOn(status -> status == JoinInviteRoomResult.Status.FULL)
				.hasSize(4);
			assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY)).isEqualTo(2);
			assertThat(redisTemplate.opsForHash().values(PARTICIPANTS_KEY))
				.extracting(Object::toString)
				.anySatisfy(value -> assertThat(value).contains("\"slotNo\":1"))
				.anySatisfy(value -> assertThat(value).contains("\"slotNo\":2"));
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void waitingHostLeaveClosesRoomAndPreservesParticipants() {
		setupFullRoom();

		assertThat(store.findRoomMetadata(ROOM_ID))
			.contains(new WaitingRoomMetadata(
				ROOM_ID,
				RoomType.INVITE,
				RoomStatus.WAITING,
				"0123"
			));
		assertThat(store.leaveAtomically(leaveCommand("USER:1")))
			.isEqualTo(LeaveWaitingRoomResult.ROOM_CLOSED);

		assertThat(redisTemplate.opsForHash().get(ROOM_KEY, "roomStatus"))
			.isEqualTo("CLOSED");
		assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY)).isEqualTo(2);
		assertThat(redisTemplate.opsForHash().hasKey(PARTICIPANTS_KEY, "USER:1")).isTrue();
		assertThat(redisTemplate.opsForHash().hasKey(PARTICIPANTS_KEY, "GUEST:one")).isTrue();
		assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isFalse();
		assertClosedTtls();
	}

	@Test
	void waitingPlayerLeaveRemovesOnlyPlayerAndRenewsActiveTtl() {
		setupFullRoom();
		redisTemplate.expire(ROOM_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(PARTICIPANTS_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(INVITE_CODE_KEY, Duration.ofMinutes(5));

		assertThat(store.leaveAtomically(leaveCommand("GUEST:one")))
			.isEqualTo(LeaveWaitingRoomResult.LEFT);

		assertThat(redisTemplate.opsForHash().get(ROOM_KEY, "roomStatus"))
			.isEqualTo("WAITING");
		assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY)).isEqualTo(1);
		assertThat(redisTemplate.opsForHash().hasKey(PARTICIPANTS_KEY, "USER:1")).isTrue();
		assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isTrue();
		assertRenewedTtls();

		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:two")).snapshot()
			.participants())
			.filteredOn(participant -> participant.participantKey().equals("GUEST:two"))
			.extracting(participant -> participant.slotNo())
			.containsExactly(2);
	}

	@Test
	void countdownHostAndPlayerLeaveBothCloseWithoutRemovingParticipants() {
		for (String participantKey : List.of("USER:1", "GUEST:one")) {
			tearDown();
			setupFullRoom();
			redisTemplate.opsForHash().put(ROOM_KEY, "roomStatus", "COUNTDOWN");

			assertThat(store.leaveAtomically(leaveCommand(participantKey)))
				.isEqualTo(LeaveWaitingRoomResult.ROOM_CLOSED);
			assertThat(redisTemplate.opsForHash().get(ROOM_KEY, "roomStatus"))
				.isEqualTo("CLOSED");
			assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY)).isEqualTo(2);
			assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isFalse();
			assertClosedTtls();
		}
	}

	@Test
	void closedExistingParticipantIsIdempotentWithoutRenewingTtl() {
		setupFullRoom();
		store.leaveAtomically(leaveCommand("USER:1"));
		redisTemplate.expire(ROOM_KEY, Duration.ofSeconds(10));
		redisTemplate.expire(PARTICIPANTS_KEY, Duration.ofSeconds(10));

		assertThat(store.leaveAtomically(leaveCommand("USER:1")))
			.isEqualTo(LeaveWaitingRoomResult.ALREADY_CLOSED);

		assertThat(redisTemplate.getExpire(ROOM_KEY, TimeUnit.SECONDS)).isBetween(8L, 10L);
		assertThat(redisTemplate.getExpire(PARTICIPANTS_KEY, TimeUnit.SECONDS))
			.isBetween(8L, 10L);
		assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isFalse();
	}

	@Test
	void nonParticipantAndRepeatedWaitingPlayerLeaveDoNotRenewTtl() {
		setupFullRoom();
		assertThat(store.leaveAtomically(leaveCommand("GUEST:one")))
			.isEqualTo(LeaveWaitingRoomResult.LEFT);
		redisTemplate.expire(ROOM_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(PARTICIPANTS_KEY, Duration.ofMinutes(5));
		redisTemplate.expire(INVITE_CODE_KEY, Duration.ofMinutes(5));

		assertThat(store.leaveAtomically(leaveCommand("GUEST:one")))
			.isEqualTo(LeaveWaitingRoomResult.PARTICIPANT_NOT_FOUND);
		assertThat(store.leaveAtomically(leaveCommand("USER:other")))
			.isEqualTo(LeaveWaitingRoomResult.PARTICIPANT_NOT_FOUND);
		assertThat(redisTemplate.getExpire(ROOM_KEY, TimeUnit.SECONDS))
			.isBetween(290L, 300L);
		assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY)).isEqualTo(1);
	}

	@Test
	void inviteIndexMustMatchBeforeClosing() {
		setupFullRoom();
		redisTemplate.delete(INVITE_CODE_KEY);
		assertThat(store.leaveAtomically(leaveCommand("USER:1")))
			.isEqualTo(LeaveWaitingRoomResult.CORRUPTED);
		assertThat(redisTemplate.opsForHash().get(ROOM_KEY, "roomStatus"))
			.isEqualTo("WAITING");

		String otherRoomId = UUID.randomUUID().toString();
		redisTemplate.opsForValue().set(INVITE_CODE_KEY, otherRoomId, TTL);
		assertThat(store.leaveAtomically(leaveCommand("USER:1")))
			.isEqualTo(LeaveWaitingRoomResult.CORRUPTED);
		assertThat(redisTemplate.opsForValue().get(INVITE_CODE_KEY)).isEqualTo(otherRoomId);
	}

	@Test
	void malformedMetadataAndParticipantAreStoreUnavailableResults() {
		store.createInviteRoomAtomically(command());
		redisTemplate.opsForHash().delete(ROOM_KEY, "roomCode");
		assertThatThrownBy(() -> store.findRoomMetadata(ROOM_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));

		tearDown();
		store.createInviteRoomAtomically(command());
		redisTemplate.opsForHash().put(PARTICIPANTS_KEY, "USER:1", "not-json");
		assertThat(store.leaveAtomically(leaveCommand("USER:1")))
			.isEqualTo(LeaveWaitingRoomResult.CORRUPTED);
	}

	@Test
	void samePlayerConcurrentLeaveHasOneSuccess() throws Exception {
		setupFullRoom();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<LeaveWaitingRoomResult>> results = executor.invokeAll(List.of(
				() -> store.leaveAtomically(leaveCommand("GUEST:one")),
				() -> store.leaveAtomically(leaveCommand("GUEST:one"))
			));
			assertThat(results)
				.extracting(future -> future.get())
				.containsExactlyInAnyOrder(
					LeaveWaitingRoomResult.LEFT,
					LeaveWaitingRoomResult.PARTICIPANT_NOT_FOUND
				);
			assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY)).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void hostAndPlayerConcurrentLeaveEndsClosedConsistently() throws Exception {
		setupFullRoom();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			executor.invokeAll(List.of(
				() -> store.leaveAtomically(leaveCommand("USER:1")),
				() -> store.leaveAtomically(leaveCommand("GUEST:one"))
			));

			assertThat(redisTemplate.opsForHash().get(ROOM_KEY, "roomStatus"))
				.isEqualTo("CLOSED");
			assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isFalse();
			assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY))
				.isBetween(1L, 2L);
			assertClosedTtls();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void playerLeaveAndNewJoinNeverExceedCapacityOrDuplicateSlots() throws Exception {
		setupFullRoom();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<LeaveWaitingRoomResult> leave = executor.submit(
				() -> store.leaveAtomically(leaveCommand("GUEST:one"))
			);
			Future<JoinInviteRoomResult> join = executor.submit(
				() -> store.joinInviteRoomAtomically(joinCommand("GUEST:two"))
			);

			assertThat(leave.get()).isEqualTo(LeaveWaitingRoomResult.LEFT);
			assertThat(join.get().status())
				.isIn(
					JoinInviteRoomResult.Status.JOINED,
					JoinInviteRoomResult.Status.FULL
				);
			assertThat(redisTemplate.opsForHash().size(PARTICIPANTS_KEY))
				.isBetween(1L, 2L);
			assertThat(redisTemplate.opsForHash().values(PARTICIPANTS_KEY))
				.extracting(Object::toString)
				.extracting(value -> value.replaceAll(".*\"slotNo\":([0-9]+).*", "$1"))
				.doesNotHaveDuplicates();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void stateCommandsAtomicallyReachCountdownAndInGame() {
		setupFullRoom();
		completeCalibration("USER:1");
		completeCalibration("GUEST:one");
		assertThat(
			store.updateReadyAtomically(
				new UpdateReadyCommand(
					ROOM_ID,
					"0123",
					"GUEST:one",
					true,
					2,
					TTL
				)
			)
		).isEqualTo(UpdateReadyResult.UPDATED);

		UUID countdownId =
			UUID.fromString("9e3c76b2-7f78-4275-b8af-7cdd921bbb4f");
		Instant countdownEndsAt =
			Instant.parse("2026-07-30T04:02:03Z");
		StartInviteGameCommand start = new StartInviteGameCommand(
			ROOM_ID,
			"0123",
			"USER:1",
			countdownId,
			countdownEndsAt,
			2,
			TTL
		);

		StartInviteGameResult started = store.startInviteGameAtomically(start);
		StartInviteGameResult duplicate = store.startInviteGameAtomically(start);

		assertThat(started.status())
			.isEqualTo(StartInviteGameResult.Status.STARTED);
		assertThat(duplicate.status())
			.isEqualTo(StartInviteGameResult.Status.ALREADY_COUNTDOWN);
		assertThat(duplicate.countdownId()).isEqualTo(countdownId);
		assertThat(store.findSnapshot(ROOM_ID).orElseThrow().room())
			.satisfies(room -> {
				assertThat(room.roomStatus()).isEqualTo(RoomStatus.COUNTDOWN);
				assertThat(room.countdownEndsAt()).isEqualTo(countdownEndsAt);
			});
		assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isTrue();

		assertThat(
			store.completeCountdownAtomically(
				new CompleteCountdownCommand(
					ROOM_ID,
					"0123",
					countdownId,
					countdownEndsAt,
					2,
					TTL
				)
			)
		).isEqualTo(CompleteCountdownResult.STARTED);
		assertThat(store.findSnapshot(ROOM_ID).orElseThrow().room())
			.satisfies(room -> {
				assertThat(room.roomStatus()).isEqualTo(RoomStatus.IN_GAME);
				assertThat(room.countdownId()).isNull();
				assertThat(room.countdownEndsAt()).isNull();
			});
		assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isFalse();
		assertThat(store.leaveAtomically(leaveCommand("USER:1")))
			.isEqualTo(LeaveWaitingRoomResult.NOT_JOINABLE);
	}

	@Test
	void recalibrationClearsReadyAndRollbackRestoresWaiting() {
		setupFullRoom();
		completeCalibration("USER:1");
		completeCalibration("GUEST:one");
		store.updateReadyAtomically(
			new UpdateReadyCommand(
				ROOM_ID,
				"0123",
				"GUEST:one",
				true,
				2,
				TTL
			)
		);

		assertThat(
			store.updateCalibrationAtomically(
				new UpdateCalibrationCommand(
					ROOM_ID,
					"0123",
					"GUEST:one",
					CalibrationStatus.IN_PROGRESS,
					2,
					TTL
				)
			)
		).isEqualTo(UpdateCalibrationResult.UPDATED);
		assertThat(
			store.findSnapshot(ROOM_ID).orElseThrow().participants().stream()
				.filter(participant ->
					participant.participantKey().equals("GUEST:one"))
				.findFirst()
				.orElseThrow()
				.isReady()
		).isFalse();

		assertThat(
			store.updateCalibrationAtomically(
				new UpdateCalibrationCommand(
					ROOM_ID,
					"0123",
					"GUEST:one",
					CalibrationStatus.COMPLETED,
					2,
					TTL
				)
			)
		).isEqualTo(UpdateCalibrationResult.UPDATED);
		store.updateReadyAtomically(
			new UpdateReadyCommand(
				ROOM_ID,
				"0123",
				"GUEST:one",
				true,
				2,
				TTL
			)
		);
		UUID countdownId = UUID.randomUUID();
		Instant endsAt = Instant.parse("2026-07-30T04:03:03Z");
		store.startInviteGameAtomically(
			new StartInviteGameCommand(
				ROOM_ID,
				"0123",
				"USER:1",
				countdownId,
				endsAt,
				2,
				TTL
			)
		);

		assertThat(
			store.rollbackCountdownAtomically(
				new RollbackCountdownCommand(
					ROOM_ID,
					"0123",
					countdownId,
					TTL
				)
			)
		).isEqualTo(RollbackCountdownResult.ROLLED_BACK);
		assertThat(store.findSnapshot(ROOM_ID).orElseThrow().room().roomStatus())
			.isEqualTo(RoomStatus.WAITING);
		assertThat(redisTemplate.hasKey(INVITE_CODE_KEY)).isTrue();
	}

	CreateInviteRoomCommand command() {
		Instant now = Instant.parse("2026-07-30T04:00:00Z");
		return new CreateInviteRoomCommand(
			new WaitingRoom(
				ROOM_ID,
				RoomType.INVITE,
				GameName.EYEFIGHT,
				"0123",
				RoomStatus.WAITING,
				now
			),
			new WaitingRoomParticipant(
				"USER:1",
				"회원닉네임",
				RoomRole.HOST,
				1,
				false,
				CalibrationStatus.PENDING,
				now
			),
			TTL
		);
	}

	private JoinInviteRoomCommand joinCommand(String participantKey) {
		return joinCommandWithCapacity(participantKey, 2);
	}

	private LeaveWaitingRoomCommand leaveCommand(String participantKey) {
		return new LeaveWaitingRoomCommand(
			ROOM_ID,
			"0123",
			participantKey,
			2,
			TTL,
			Duration.ofSeconds(30)
		);
	}

	private void setupFullRoom() {
		store.createInviteRoomAtomically(command());
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.JOINED);
	}

	private void completeCalibration(String participantKey) {
		assertThat(
			store.updateCalibrationAtomically(
				new UpdateCalibrationCommand(
					ROOM_ID,
					"0123",
					participantKey,
					CalibrationStatus.IN_PROGRESS,
					2,
					TTL
				)
			)
		).isEqualTo(UpdateCalibrationResult.UPDATED);
		assertThat(
			store.updateCalibrationAtomically(
				new UpdateCalibrationCommand(
					ROOM_ID,
					"0123",
					participantKey,
					CalibrationStatus.COMPLETED,
					2,
					TTL
				)
			)
		).isEqualTo(UpdateCalibrationResult.UPDATED);
	}

	private JoinInviteRoomCommand joinCommandWithCapacity(
		String participantKey,
		int maxParticipants
	) {
		return new JoinInviteRoomCommand(
			ROOM_ID,
			"0123",
			participantKey,
			"입장참가자",
			Instant.parse("2026-07-30T04:01:00Z"),
			maxParticipants,
			TTL
		);
	}

	private void assertCorruptedParticipantJson(String json) {
		redisTemplate.opsForHash().put(PARTICIPANTS_KEY, "USER:1", json);
		assertThat(store.joinInviteRoomAtomically(joinCommand("GUEST:one")).status())
			.isEqualTo(JoinInviteRoomResult.Status.CORRUPTED);
	}

	private void assertRenewedTtls() {
		List<Long> ttls = List.of(ROOM_KEY, PARTICIPANTS_KEY, INVITE_CODE_KEY)
			.stream()
			.map(key -> redisTemplate.getExpire(key, TimeUnit.SECONDS))
			.toList();
		assertThat(ttls).allSatisfy(ttl -> assertThat(ttl).isBetween(590L, 600L));
		assertThat(ttls.stream().mapToLong(Long::longValue).max().orElseThrow()
			- ttls.stream().mapToLong(Long::longValue).min().orElseThrow())
			.isLessThanOrEqualTo(1L);
	}

	private void assertClosedTtls() {
		List<Long> ttls = List.of(ROOM_KEY, PARTICIPANTS_KEY)
			.stream()
			.map(key -> redisTemplate.getExpire(key, TimeUnit.SECONDS))
			.toList();
		assertThat(ttls).allSatisfy(ttl -> assertThat(ttl).isBetween(25L, 30L));
		assertThat(ttls.stream().mapToLong(Long::longValue).max().orElseThrow()
			- ttls.stream().mapToLong(Long::longValue).min().orElseThrow())
			.isLessThanOrEqualTo(1L);
	}

	private WaitingRoomParticipant randomParticipant(
		String participantKey,
		int slotNo,
		Instant joinedAt
	) {
		return new WaitingRoomParticipant(
			participantKey,
			"참가자" + slotNo,
			RoomRole.PLAYER,
			slotNo,
			false,
			CalibrationStatus.PENDING,
			joinedAt
		);
	}

	private void createRandomRoom() {
		Instant createdAt = Instant.parse("2026-07-30T04:00:00Z");
		WaitingRoom room = new WaitingRoom(
			ROOM_ID,
			RoomType.RANDOM,
			GameName.EYEFIGHT,
			null,
			RoomStatus.WAITING,
			createdAt
		);
		assertThat(
			store.createRandomRoomAtomically(
				new CreateRandomRoomCommand(
					room,
					List.of(
						randomParticipant("USER:1", 1, createdAt),
						randomParticipant(
							"GUEST:00000000-0000-0000-0000-000000000002",
							2,
							createdAt
						)
					),
					TTL
				)
			)
		).isTrue();
	}

	private void completeRandomCalibration(String participantKey) {
		assertThat(
			store.updateCalibrationAtomically(
				new UpdateCalibrationCommand(
					ROOM_ID,
					null,
					participantKey,
					CalibrationStatus.IN_PROGRESS,
					2,
					TTL
				)
			)
		).isEqualTo(UpdateCalibrationResult.UPDATED);
		assertThat(
			store.updateCalibrationAtomically(
				new UpdateCalibrationCommand(
					ROOM_ID,
					null,
					participantKey,
					CalibrationStatus.COMPLETED,
					2,
					TTL
				)
			)
		).isEqualTo(UpdateCalibrationResult.UPDATED);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		RedisKeyBuilder redisKeyBuilder() {
			return new RedisKeyBuilder("test");
		}

		@Bean
		JsonMapper jsonMapper() {
			return JsonMapper.builder().findAndAddModules().build();
		}
	}
}
