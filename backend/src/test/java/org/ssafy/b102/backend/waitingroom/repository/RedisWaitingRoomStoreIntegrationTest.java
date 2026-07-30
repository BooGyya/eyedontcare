package org.ssafy.b102.backend.waitingroom.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
