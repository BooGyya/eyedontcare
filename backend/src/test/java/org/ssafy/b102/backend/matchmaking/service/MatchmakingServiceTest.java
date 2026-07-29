package org.ssafy.b102.backend.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.config.RedisConfig;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.matchmaking.dto.response.MatchStatusResponse;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository;
import org.ssafy.b102.backend.waitingroom.service.RandomRoomCreator;

@DataRedisTest
@Import({
	RedisConfig.class,
	RedisKeyBuilder.class,
	MatchmakingEntryRepository.class,
	MatchmakingService.class,
	MatchmakingServiceTest.TestRoomCreatorConfig.class
})
class MatchmakingServiceTest {

	private static final String REQUESTER_KEY = "USER:1";
	private static final String OPPONENT_KEY = "USER:2";
	private static final String THIRD_KEY = "GUEST:3f2a1c9e";
	private static final String GAME_TYPE = "HOCKEY";

	@Autowired
	private MatchmakingService matchmakingService;

	@Autowired
	private MatchmakingEntryRepository matchmakingEntryRepository;

	@Autowired
	private ControllableRoomCreator roomCreator;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private RedisKeyBuilder redisKeyBuilder;

	@BeforeEach
	void setUp() {
		roomCreator.succeedAlways();
		List.of(REQUESTER_KEY, OPPONENT_KEY, THIRD_KEY)
			.forEach(matchmakingEntryRepository::delete);
		stringRedisTemplate.delete(queueKey(GAME_TYPE));
		stringRedisTemplate.delete(queueKey(GameName.BLINK.name()));
	}

	@Test
	void joinRegistersEntryInSearchingState() {
		MatchStatusResponse response = matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> {
				assertThat(entry.participantKey()).isEqualTo(REQUESTER_KEY);
				assertThat(entry.gameType()).isEqualTo(GameName.HOCKEY);
				assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
				assertThat(entry.waitingRoomId()).isNull();
				assertThat(entry.queuedAt()).isNotNull();
				assertThat(entry.statusChangedAt()).isNotNull();
			});
	}

	@Test
	void joinRejectsDuplicateRequest() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		assertThatThrownBy(() -> matchmakingService.join(REQUESTER_KEY, GAME_TYPE))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(MatchmakingErrorCode.ALREADY_IN_QUEUE));
	}

	/**
	 * 기능 정의서: 최초 신청 시각을 {@code queued_at}으로 기록한다.
	 */
	@Test
	void duplicateJoinDoesNotOverwriteQueuedAt() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		Instant firstQueuedAt = matchmakingEntryRepository.find(REQUESTER_KEY).orElseThrow().queuedAt();

		assertThatThrownBy(() -> matchmakingService.join(REQUESTER_KEY, GAME_TYPE))
			.isInstanceOf(BusinessException.class);

		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> assertThat(entry.queuedAt()).isEqualTo(firstQueuedAt));
	}

	@Test
	void joinKeepsSearchingWhenNoOpponentWaits() {
		MatchStatusResponse response = matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isEqualTo(1L);
	}

	@Test
	void joinMatchesTwoWaitingParticipants() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		MatchStatusResponse response = matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.ENTERING_ROOM);
		UUID roomId = matchmakingEntryRepository.find(OPPONENT_KEY).orElseThrow().waitingRoomId();
		assertThat(roomId).isNotNull();
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> {
				assertThat(entry.matchStatus()).isEqualTo(MatchStatus.ENTERING_ROOM);
				assertThat(entry.waitingRoomId()).isEqualTo(roomId);
			});
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isZero();
	}

	@Test
	void joinMatchesEarliestParticipantsFirst() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		MatchStatusResponse response = matchmakingService.join(THIRD_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
	}

	/**
	 * 기능 정의서: 방 생성에 실패하면 선점을 해제하고 두 참가자의 기존 queued_at을 유지한다.
	 */
	@Test
	void joinRestoresQueueWhenRoomCreationFails() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		Instant firstQueuedAt = matchmakingEntryRepository.find(REQUESTER_KEY).orElseThrow().queuedAt();
		roomCreator.failAlways();

		MatchStatusResponse response = matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isEqualTo(2L);
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> {
				assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
				assertThat(entry.queuedAt()).isEqualTo(firstQueuedAt);
			});
		assertThat(stringRedisTemplate.opsForZSet().score(queueKey(GAME_TYPE), REQUESTER_KEY))
			.isEqualTo((double) firstQueuedAt.toEpochMilli());
	}

	/**
	 * 큐에서 두 명을 꺼낸 뒤 대기방 생성이 예외로 실패해도 선점을 해제해야 한다.
	 *
	 * <p>큐를 조작한 것은 matchmaking이므로 되돌릴 책임도 matchmaking에 있다.
	 * 되돌리지 못하면 두 참가자는 큐에서 사라진 채 SEARCHING으로 남아 TTL까지 매칭되지 않는다.
	 */
	@Test
	void joinRestoresQueueWhenRoomCreationThrows() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		Instant firstQueuedAt = matchmakingEntryRepository.find(REQUESTER_KEY).orElseThrow().queuedAt();
		roomCreator.throwAlways();

		MatchStatusResponse response = matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isEqualTo(2L);
		assertThat(stringRedisTemplate.opsForZSet().score(queueKey(GAME_TYPE), REQUESTER_KEY))
			.isEqualTo((double) firstQueuedAt.toEpochMilli());
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
	}

	/**
	 * entry는 TTL로 사라지지만 Sorted Set member는 만료되지 않는다.
	 * 남은 유령 member로 방이 만들어지면 안 된다.
	 */
	@Test
	void joinSkipsGhostQueueMemberWithoutEntry() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		stringRedisTemplate.delete(entryKey(REQUESTER_KEY));

		MatchStatusResponse response = matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isEqualTo(1L);
	}

	@Test
	void joinDoesNotMatchAcrossDifferentGameTypes() {
		matchmakingService.join(REQUESTER_KEY, GameName.HOCKEY.name());

		MatchStatusResponse response = matchmakingService.join(OPPONENT_KEY, GameName.BLINK.name());

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
	}

	@Test
	void joinRejectsUnsupportedGameType() {
		assertThatThrownBy(() -> matchmakingService.join(REQUESTER_KEY, "CHESS"))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(MatchmakingErrorCode.INVALID_GAME_TYPE));
	}

	@Test
	void joinRejectsInvalidParticipantKey() {
		assertThatThrownBy(() -> matchmakingService.join("ADMIN:1", GAME_TYPE))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(MatchmakingErrorCode.INVALID_PARTICIPANT_KEY));
	}

	@Test
	void cancelRemovesSearchingEntry() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		MatchStatusResponse response = matchmakingService.cancel(REQUESTER_KEY);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.CANCELLED);
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY)).isEmpty();
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isZero();
	}

	@Test
	void cancelRejectsWhenNoRequestExists() {
		assertThatThrownBy(() -> matchmakingService.cancel(REQUESTER_KEY))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(MatchmakingErrorCode.REQUEST_NOT_FOUND));
	}

	/**
	 * 기능 정의서: ENTERING_ROOM으로 전환된 후에는 취소할 수 없다.
	 */
	@Test
	void cancelRejectsAfterMatchConfirmed() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThatThrownBy(() -> matchmakingService.cancel(REQUESTER_KEY))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(MatchmakingErrorCode.CANCEL_NOT_ALLOWED));
	}

	@Test
	void entryHasExpiration() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		Long ttlSeconds = stringRedisTemplate.getExpire(entryKey(REQUESTER_KEY));

		assertThat(ttlSeconds).isPositive();
	}

	private String queueKey(String gameType) {
		return redisKeyBuilder.build("matchmaking", "queue", gameType);
	}

	private String entryKey(String participantKey) {
		return redisKeyBuilder.build("matchmaking", "entry", participantKey);
	}

	static class ControllableRoomCreator implements RandomRoomCreator {

		private enum Behavior { SUCCEED, RETURN_EMPTY, THROW }

		private Behavior behavior = Behavior.SUCCEED;

		void succeedAlways() {
			this.behavior = Behavior.SUCCEED;
		}

		void failAlways() {
			this.behavior = Behavior.RETURN_EMPTY;
		}

		void throwAlways() {
			this.behavior = Behavior.THROW;
		}

		@Override
		public Optional<UUID> createRandomRoom(GameName gameType, List<String> participantKeys) {
			return switch (behavior) {
				case SUCCEED -> Optional.of(UUID.randomUUID());
				case RETURN_EMPTY -> Optional.empty();
				case THROW -> throw new IllegalStateException("대기방 생성 실패를 예외로 알리는 구현체");
			};
		}
	}

	static class TestRoomCreatorConfig {

		@Bean
		@Primary
		ControllableRoomCreator controllableRoomCreator() {
			return new ControllableRoomCreator();
		}
	}
}
