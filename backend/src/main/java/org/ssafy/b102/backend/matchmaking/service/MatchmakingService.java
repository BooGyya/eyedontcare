package org.ssafy.b102.backend.matchmaking.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.matchmaking.dto.response.MatchStatusResponse;
import org.ssafy.b102.backend.matchmaking.entity.MatchmakingEntry;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;
import org.ssafy.b102.backend.user.repository.UserRepository;
import org.ssafy.b102.backend.waitingroom.service.RandomRoomCreator;

/**
 * 랜덤 매칭 신청과 취소.
 *
 * <p>매칭 성사는 별도 스케줄러 없이 신청 시점에 즉시 시도한다. 2인 매칭이라 대기자가 있으면
 * 신청과 동시에 성사되므로 지연이 생기지 않는다.
 *
 * <p>Redis 트랜잭션을 쓰지 않는다. 명령 자체의 원자성으로 경쟁 조건이 해소되기 때문이며
 * 근거는 {@link MatchmakingEntryRepository}에 적어두었다.
 *
 * <p>{@link RandomRoomCreator}를 {@link ObjectProvider}로 받는 이유는 WaitingRoom 도메인이
 * 아직 없기 때문이다. 구현체가 없으면 매칭 성사 단계를 건너뛰고 참가자는 계속 대기한다.
 * 방이 없는 상태에서 성사시켜도 입장할 곳이 없으므로 이것이 정직한 동작이다.
 * 가짜 구현체를 두지 않았기 때문에 담당자가 구현체를 추가하면 그대로 동작하며,
 * 빈 충돌이나 삭제해야 할 파일이 생기지 않는다.
 */
@Service
public class MatchmakingService {

	private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

	private static final int MATCH_SIZE = 2;
	private static final String USER_KEY_PREFIX = "USER:";
	private static final String GUEST_KEY_PREFIX = "GUEST:";

	private final MatchmakingEntryRepository matchmakingEntryRepository;
	private final ObjectProvider<RandomRoomCreator> randomRoomCreator;
	private final MatchNotifier matchNotifier;
	private final UserRepository userRepository;
	private final GuestSessionService guestSessionService;

	public MatchmakingService(
		MatchmakingEntryRepository matchmakingEntryRepository,
		ObjectProvider<RandomRoomCreator> randomRoomCreator,
		MatchNotifier matchNotifier,
		UserRepository userRepository,
		GuestSessionService guestSessionService
	) {
		this.matchmakingEntryRepository = matchmakingEntryRepository;
		this.randomRoomCreator = randomRoomCreator;
		this.matchNotifier = matchNotifier;
		this.userRepository = userRepository;
		this.guestSessionService = guestSessionService;
	}

	public MatchStatusResponse join(String participantKey, String gameType) {
		validateParticipant(participantKey);
		GameName resolvedGameType = resolveGameType(gameType);

		MatchmakingEntry requested =
			MatchmakingEntry.searching(participantKey, resolvedGameType, Instant.now());
		if (!matchmakingEntryRepository.enqueue(requested)) {
			throw new BusinessException(MatchmakingErrorCode.ALREADY_IN_QUEUE);
		}

		tryMatch(resolvedGameType);

		return MatchStatusResponse.from(
			matchmakingEntryRepository.find(participantKey).orElse(requested)
		);
	}

	public MatchStatusResponse cancel(String participantKey) {
		validateParticipant(participantKey);

		MatchmakingEntry entry = matchmakingEntryRepository.find(participantKey)
			.orElseThrow(() -> new BusinessException(MatchmakingErrorCode.REQUEST_NOT_FOUND));

		if (!entry.isSearching()) {
			throw new BusinessException(MatchmakingErrorCode.CANCEL_NOT_ALLOWED);
		}

		matchmakingEntryRepository.delete(participantKey);

		return MatchStatusResponse.cancelled(entry);
	}

	/**
	 * WebSocket 연결 종료 시의 정리. REST {@link #cancel}과 달리 조용해야 한다.
	 *
	 * <p>신청이 없어도 예외를 던지지 않고, 이미 {@code ENTERING_ROOM}이면 취소하지 않는다.
	 * 성사되어 대기방 입장 흐름이 진행 중인 참가자를 연결 끊김만으로 되돌려서는 안 되기 때문이다.
	 * 아직 {@code SEARCHING}인 참가자만 큐와 상태에서 정리한다.
	 */
	public void cancelSilently(String participantKey) {
		matchmakingEntryRepository.find(participantKey)
			.filter(MatchmakingEntry::isSearching)
			.ifPresent(entry -> matchmakingEntryRepository.delete(participantKey));
	}

	/**
	 * 현재 참가자 상태를 조회한다. WebSocket 연결 시점에 이미 성사돼 있으면 즉시 알림을 재전송하기 위해 쓴다.
	 */
	public Optional<MatchmakingEntry> findEntry(String participantKey) {
		return matchmakingEntryRepository.find(participantKey);
	}

	/**
	 * 대기 순서가 빠른 두 명을 하나의 시도로 예약하고 대기방 생성을 요청한다.
	 *
	 * <p>흐름: 예약({@code MATCHING}) → 방 생성 → 같은 시도 재검증 후 {@code ENTERING_ROOM} 확정 →
	 * {@code MATCH_SUCCESS} 전송. 방 생성 실패(빈 값·예외)나 finalize 실패는 모두 보상한다.
	 */
	private void tryMatch(GameName gameType) {
		RandomRoomCreator roomCreator = randomRoomCreator.getIfAvailable();
		if (roomCreator == null) {
			log.warn("RandomRoomCreator 구현체가 없어 매칭을 성사시키지 않습니다. gameType={}", gameType);
			return;
		}

		UUID matchAttemptId = UUID.randomUUID();
		List<MatchmakingEntry> reserved =
			matchmakingEntryRepository.reserveCandidates(gameType, matchAttemptId, MATCH_SIZE);
		if (reserved.isEmpty()) {
			return;
		}

		List<String> participantKeys = reserved.stream()
			.map(MatchmakingEntry::participantKey)
			.toList();

		Optional<UUID> waitingRoomId = createRoom(roomCreator, gameType, participantKeys);
		if (waitingRoomId.isEmpty()) {
			compensate(reserved, matchAttemptId);
			return;
		}

		boolean finalized =
			matchmakingEntryRepository.finalizeToRoom(reserved, matchAttemptId, waitingRoomId.get());
		if (!finalized) {
			log.warn(
				"finalize에 실패했습니다. 생성된 RANDOM 대기방은 TTL로 만료됩니다. roomId={}, attemptId={}",
				waitingRoomId.get(),
				matchAttemptId
			);
			compensate(reserved, matchAttemptId);
			return;
		}

		reserved.forEach(entry ->
			matchNotifier.notifyMatched(entry.participantKey(), waitingRoomId.get(), gameType));
	}

	/**
	 * 방 생성·finalize 실패 보상. 유효한 참가자만 현재 시각 기준으로 다시 큐에 넣고,
	 * 무효한 참가자(탈퇴 회원·만료 게스트)는 정리한다. 기존 대기 순서는 승계하지 않는다.
	 *
	 * <p>생성됐을 수 있는 orphan RANDOM 대기방은 별도로 정리하지 않고 TTL(10분) 만료에 맡긴다(팀 합의).
	 */
	private void compensate(List<MatchmakingEntry> reserved, UUID matchAttemptId) {
		for (MatchmakingEntry entry : reserved) {
			if (isParticipantValid(entry.participantKey())) {
				matchmakingEntryRepository.reregisterAtCurrentTime(entry, matchAttemptId);
			} else {
				matchmakingEntryRepository.delete(entry.participantKey());
			}
		}
	}

	/**
	 * 재등록 전 참가자가 여전히 유효한지 확인한다. 회원은 존재·미탈퇴, 게스트는 세션 유효.
	 */
	private boolean isParticipantValid(String participantKey) {
		if (participantKey.startsWith(USER_KEY_PREFIX)) {
			try {
				long userId = Long.parseLong(participantKey.substring(USER_KEY_PREFIX.length()));
				return userRepository.existsByIdAndDeletedAtIsNull(userId);
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		if (participantKey.startsWith(GUEST_KEY_PREFIX)) {
			try {
				return guestSessionService.exists(GuestParticipantKey.parse(participantKey).guestSessionId());
			} catch (RuntimeException exception) {
				return false;
			}
		}

		return false;
	}

	/**
	 * 대기방 생성 실패를 모두 빈 값으로 모은다.
	 *
	 * <p>규약은 실패를 빈 {@link Optional}로 알리는 것이지만, 구현체가 예외를 던져도 결과는 같아야 한다.
	 * 큐에서 참가자를 꺼낸 것은 matchmaking이므로 되돌릴 책임도 matchmaking에 있다.
	 * 예외를 삼키지 않고 ERROR로 남긴 뒤 호출자가 선점을 해제하게 한다.
	 */
	private Optional<UUID> createRoom(
		RandomRoomCreator roomCreator,
		GameName gameType,
		List<String> participantKeys
	) {
		try {
			return roomCreator.createRandomRoom(gameType, participantKeys);
		} catch (RuntimeException exception) {
			log.error(
				"대기방 생성 중 예외가 발생해 선점을 해제합니다. gameType={}, participants={}",
				gameType,
				participantKeys,
				exception
			);

			return Optional.empty();
		}
	}

	/**
	 * 임시 인증. 인증 도메인이 완성되면 JWT에서 추출한 참가자 키로 대체된다.
	 *
	 * <p>REST 진입점과 WebSocket 핸들러가 같은 규칙으로 키를 검증하도록 public으로 공개한다.
	 */
	public void validateParticipant(String participantKey) {
		if (participantKey == null) {
			throw new BusinessException(MatchmakingErrorCode.INVALID_PARTICIPANT_KEY);
		}

		boolean member = hasIdentifierAfter(participantKey, USER_KEY_PREFIX);
		boolean guest = hasIdentifierAfter(participantKey, GUEST_KEY_PREFIX);
		if (!member && !guest) {
			throw new BusinessException(MatchmakingErrorCode.INVALID_PARTICIPANT_KEY);
		}
	}

	private static boolean hasIdentifierAfter(String participantKey, String prefix) {
		return participantKey.startsWith(prefix) && participantKey.length() > prefix.length();
	}

	private static GameName resolveGameType(String gameType) {
		try {
			return GameName.valueOf(gameType.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(MatchmakingErrorCode.INVALID_GAME_TYPE);
		}
	}
}
