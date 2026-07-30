package org.ssafy.b102.backend.waitingroom.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
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
