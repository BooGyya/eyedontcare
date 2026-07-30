package org.ssafy.b102.backend.waitingroom.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import tools.jackson.databind.json.JsonMapper;

class RedisWaitingRoomStoreTest {

	@Test
	void convertsRedisFailureToWaitingRoomStoreUnavailable() {
		StringRedisTemplate redisTemplate = new StringRedisTemplate() {
			@Override
			public <T> T execute(
				RedisScript<T> script,
				List<String> keys,
				Object... args
			) {
				throw new RedisConnectionFailureException("unavailable");
			}
		};
		RedisWaitingRoomStore store = new RedisWaitingRoomStore(
			redisTemplate,
			new RedisKeyBuilder("test"),
			JsonMapper.builder().findAndAddModules().build()
		);
		RedisWaitingRoomStoreIntegrationTest fixture =
			new RedisWaitingRoomStoreIntegrationTest();

		assertThatThrownBy(() -> store.createInviteRoomAtomically(fixture.command()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));
	}

	@Test
	void convertsJoinRedisFailureToWaitingRoomStoreUnavailable() {
		StringRedisTemplate redisTemplate = new StringRedisTemplate() {
			@Override
			public <T> T execute(
				RedisScript<T> script,
				List<String> keys,
				Object... args
			) {
				throw new RedisConnectionFailureException("unavailable");
			}
		};
		RedisWaitingRoomStore store = new RedisWaitingRoomStore(
			redisTemplate,
			new RedisKeyBuilder("test"),
			JsonMapper.builder().findAndAddModules().build()
		);

		assertThatThrownBy(() -> store.joinInviteRoomAtomically(joinCommand()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));
	}

	@Test
	void convertsUnexpectedJoinScriptResultToStoreUnavailable() {
		StringRedisTemplate redisTemplate = new StringRedisTemplate() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T execute(
				RedisScript<T> script,
				List<String> keys,
				Object... args
			) {
				return (T) "{\"status\":\"UNKNOWN\"}";
			}
		};
		RedisWaitingRoomStore store = new RedisWaitingRoomStore(
			redisTemplate,
			new RedisKeyBuilder("test"),
			JsonMapper.builder().findAndAddModules().build()
		);

		assertThatThrownBy(() -> store.joinInviteRoomAtomically(joinCommand()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));
	}

	@Test
	void convertsLeaveRedisFailureToWaitingRoomStoreUnavailable() {
		StringRedisTemplate redisTemplate = new StringRedisTemplate() {
			@Override
			public <T> T execute(
				RedisScript<T> script,
				List<String> keys,
				Object... args
			) {
				throw new RedisConnectionFailureException("unavailable");
			}
		};
		RedisWaitingRoomStore store = new RedisWaitingRoomStore(
			redisTemplate,
			new RedisKeyBuilder("test"),
			JsonMapper.builder().findAndAddModules().build()
		);

		assertThatThrownBy(() -> store.leaveAtomically(leaveCommand()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));
	}

	@Test
	void convertsUnexpectedLeaveScriptResultToStoreUnavailable() {
		StringRedisTemplate redisTemplate = new StringRedisTemplate() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T execute(
				RedisScript<T> script,
				List<String> keys,
				Object... args
			) {
				return (T) "UNKNOWN";
			}
		};
		RedisWaitingRoomStore store = new RedisWaitingRoomStore(
			redisTemplate,
			new RedisKeyBuilder("test"),
			JsonMapper.builder().findAndAddModules().build()
		);

		assertThatThrownBy(() -> store.leaveAtomically(leaveCommand()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));
	}

	private JoinInviteRoomCommand joinCommand() {
		return new JoinInviteRoomCommand(
			UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f"),
			"0123",
			"USER:2",
			"입장참가자",
			Instant.parse("2026-07-30T04:01:00Z"),
			2,
			Duration.ofMinutes(10)
		);
	}

	private LeaveWaitingRoomCommand leaveCommand() {
		return new LeaveWaitingRoomCommand(
			UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f"),
			"0123",
			"USER:2",
			2,
			Duration.ofMinutes(10),
			Duration.ofSeconds(30)
		);
	}
}
