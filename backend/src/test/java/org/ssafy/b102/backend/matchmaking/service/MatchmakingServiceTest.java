package org.ssafy.b102.backend.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.user.repository.UserRepository;
import org.ssafy.b102.backend.waitingroom.service.RandomRoomCreator;
import org.ssafy.b102.testfixture.websocket.RecordingMatchNotifier;

@DataRedisTest
@Import({
	RedisConfig.class,
	RedisKeyBuilder.class,
	MatchmakingEntryRepository.class,
	MatchmakingService.class,
	RecordingMatchNotifier.class,
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
	private RecordingMatchNotifier matchNotifier;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private RedisKeyBuilder redisKeyBuilder;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private GuestSessionService guestSessionService;

	@BeforeEach
	void setUp() {
		roomCreator.succeedAlways();
		matchNotifier.clear();
		when(userRepository.existsByIdAndDeletedAtIsNull(anyLong())).thenReturn(true);
		when(guestSessionService.exists(any())).thenReturn(true);
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

	/**
	 * 성사되면 두 참가자 모두에게 알림 포트를 호출한다. 신청자뿐 아니라 먼저 기다리던 참가자도
	 * 자기 소켓으로 결과를 받아야 하므로, 알림은 성사가 감지되는 서비스 계층에서 일어난다.
	 */
	@Test
	void joinNotifiesBothParticipantsOnMatch() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		UUID roomId = matchmakingEntryRepository.find(OPPONENT_KEY).orElseThrow().waitingRoomId();
		assertThat(matchNotifier.notifiedKeys())
			.containsExactlyInAnyOrder(REQUESTER_KEY, OPPONENT_KEY);
		assertThat(matchNotifier.notified())
			.allSatisfy(notified -> {
				assertThat(notified.roomId()).isEqualTo(roomId);
				assertThat(notified.gameType()).isEqualTo(GameName.HOCKEY);
			});
	}

	@Test
	void joinDoesNotNotifyWhenNoOpponentWaits() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		assertThat(matchNotifier.notified()).isEmpty();
	}

	@Test
	void joinMatchesEarliestParticipantsFirst() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		MatchStatusResponse response = matchmakingService.join(THIRD_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
	}

	/**
	 * 협업 계약: 방 생성에 실패하면 예약을 풀고 유효한 참가자를 현재 시각 기준으로 다시 등록한다.
	 * 기존 대기 순서는 승계하지 않는다(예약 흔적 {@code matchAttemptId}가 지워진다).
	 */
	@Test
	void joinRestoresQueueWhenRoomCreationFails() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		roomCreator.failAlways();

		MatchStatusResponse response = matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isEqualTo(2L);
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> {
				assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
				assertThat(entry.matchAttemptId()).isNull();
			});
	}

	/**
	 * 대기방 생성이 예외로 실패해도 동일하게 보상한다(현재 시각 재등록).
	 */
	@Test
	void joinRestoresQueueWhenRoomCreationThrows() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		roomCreator.throwAlways();

		MatchStatusResponse response = matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isEqualTo(2L);
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> {
				assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
				assertThat(entry.matchAttemptId()).isNull();
			});
	}

	/**
	 * 보상 시 무효한 참가자(탈퇴 회원 등)는 재등록하지 않고 정리한다.
	 */
	@Test
	void joinDropsInvalidParticipantOnRoomFailure() {
		when(userRepository.existsByIdAndDeletedAtIsNull(2L)).thenReturn(false);
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		roomCreator.failAlways();

		matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
		assertThat(matchmakingEntryRepository.find(OPPONENT_KEY)).isEmpty();
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isEqualTo(1L);
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

	/**
	 * 이미 성사된 참가자가 재신청하면 큐에 다시 들어간다.
	 *
	 * <p>{@code ZADD NX}는 큐에 없는 member를 넣으므로, {@code ENTERING_ROOM} 참가자는
	 * (이미 큐에서 빠진 상태라) 다시 들어간다. 뒤이은 {@code HSETNX}가 실패해 409로 거절되지만
	 * 큐에는 남는다. 이 잔여 member로 두 번째 방이 만들어지면 안 된다.
	 */
	@Test
	void joinDoesNotMatchParticipantWhoAlreadyEnteredRoom() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		matchmakingService.join(OPPONENT_KEY, GAME_TYPE);
		UUID firstRoomId = matchmakingEntryRepository.find(REQUESTER_KEY).orElseThrow().waitingRoomId();

		assertThatThrownBy(() -> matchmakingService.join(REQUESTER_KEY, GAME_TYPE))
			.isInstanceOf(BusinessException.class);

		MatchStatusResponse response = matchmakingService.join(THIRD_KEY, GAME_TYPE);

		assertThat(response.matchStatus()).isEqualTo(MatchStatus.SEARCHING);
		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> {
				assertThat(entry.matchStatus()).isEqualTo(MatchStatus.ENTERING_ROOM);
				assertThat(entry.waitingRoomId()).isEqualTo(firstRoomId);
			});
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

	/**
	 * WebSocket 연결 종료 시의 조용한 취소. SEARCHING이면 신청을 정리한다.
	 */
	@Test
	void cancelSilentlyRemovesSearchingEntry() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		matchmakingService.cancelSilently(REQUESTER_KEY);

		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY)).isEmpty();
		assertThat(stringRedisTemplate.opsForZSet().size(queueKey(GAME_TYPE))).isZero();
	}

	/**
	 * 이미 성사된 참가자는 연결이 끊겨도 취소하지 않는다. 대기방 입장 흐름이 진행 중이기 때문이다.
	 */
	@Test
	void cancelSilentlyKeepsEnteringRoomEntry() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);
		matchmakingService.join(OPPONENT_KEY, GAME_TYPE);

		matchmakingService.cancelSilently(REQUESTER_KEY);

		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY))
			.get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.ENTERING_ROOM));
	}

	/**
	 * 신청이 없어도 예외를 던지지 않는다. REST의 cancel과 달리 연결 종료 정리는 조용해야 한다.
	 */
	@Test
	void cancelSilentlyDoesNothingWhenNoEntry() {
		matchmakingService.cancelSilently(REQUESTER_KEY);

		assertThat(matchmakingEntryRepository.find(REQUESTER_KEY)).isEmpty();
	}

	@Test
	void findEntryReturnsCurrentEntry() {
		matchmakingService.join(REQUESTER_KEY, GAME_TYPE);

		assertThat(matchmakingService.findEntry(REQUESTER_KEY))
			.get()
			.satisfies(entry -> assertThat(entry.matchStatus()).isEqualTo(MatchStatus.SEARCHING));
	}

	@Test
	void findEntryIsEmptyWhenNoRequestExists() {
		assertThat(matchmakingService.findEntry(REQUESTER_KEY)).isEmpty();
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
