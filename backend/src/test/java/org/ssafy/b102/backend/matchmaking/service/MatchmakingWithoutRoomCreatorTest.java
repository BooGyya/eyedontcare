package org.ssafy.b102.backend.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.config.RedisConfig;
import org.ssafy.b102.backend.matchmaking.dto.response.MatchStatusResponse;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository;
import org.ssafy.b102.backend.waitingroom.service.RandomRoomCreator;
import org.ssafy.b102.testfixture.websocket.RecordingMatchNotifier;

/**
 * WaitingRoom 도메인이 없는 현재 상태를 그대로 재현한다.
 *
 * <p>{@link RandomRoomCreator} 구현체를 등록하지 않았다. 매칭은 성사되지 않고 참가자는 계속
 * 대기해야 하며, 대기 순서도 그대로 남아 있어야 한다. 담당자가 구현체를 추가하면
 * 이 테스트만 의미를 잃고 나머지 동작은 바뀌지 않는다.
 */
@DataRedisTest
@Import({
	RedisConfig.class,
	RedisKeyBuilder.class,
	MatchmakingEntryRepository.class,
	MatchmakingService.class,
	RecordingMatchNotifier.class
})
class MatchmakingWithoutRoomCreatorTest {

	private static final String REQUESTER_KEY = "USER:11";
	private static final String OPPONENT_KEY = "USER:12";
	private static final String GAME_TYPE = "RHYTHM";

	@Autowired
	private MatchmakingService matchmakingService;

	@Autowired
	private MatchmakingEntryRepository matchmakingEntryRepository;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private RedisKeyBuilder redisKeyBuilder;

	@BeforeEach
	void setUp() {
		List.of(REQUESTER_KEY, OPPONENT_KEY).forEach(matchmakingEntryRepository::delete);
		stringRedisTemplate.delete(queueKey());
	}

	@Test
	void keepsBothParticipantsSearchingWhenRoomCreatorIsAbsent() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		MatchStatusResponse response = matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(response.waitingRoomId()).isNull();
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
	}

	@Test
	void keepsQueueOrderWhenRoomCreatorIsAbsent() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(stringRedisTemplate.opsForZSet().size(queueKey())).isEqualTo(2L);
		assertThat(stringRedisTemplate.opsForZSet().score(queueKey(), REQUESTER_KEY))
			.isEqualTo((double) matchmakingEntryRepository.find(REQUESTER_KEY)
				.orElseThrow()
				.queuedAt()
				.toEpochMilli());
	}

	@Test
	void cancelStillWorksWhenRoomCreatorIsAbsent() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		MatchStatusResponse response = matchmakingService.cancel(REQUESTER_KEY);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.CANCELLED);
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY)).isEmpty();
	}

	private String queueKey() {
		return redisKeyBuilder.build("matchmaking", "queue", GameName.valueOf(GAME_TYPE).name());
	}
}
