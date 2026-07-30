package org.ssafy.b102.backend.waitingroom.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
}
