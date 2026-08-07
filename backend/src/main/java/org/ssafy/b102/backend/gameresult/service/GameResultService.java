package org.ssafy.b102.backend.gameresult.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.gameresult.dto.request.ParticipantResultRequest;
import org.ssafy.b102.backend.gameresult.dto.request.SubmitGameResultRequest;
import org.ssafy.b102.backend.gameresult.dto.response.SubmitGameResultResponse;
import org.ssafy.b102.backend.gameresult.entity.GameResult;
import org.ssafy.b102.backend.gameresult.entity.Participant;
import org.ssafy.b102.backend.gameresult.entity.ParticipantType;
import org.ssafy.b102.backend.gameresult.exception.GameResultErrorCode;
import org.ssafy.b102.backend.gameresult.repository.GameResultRepository;
import org.ssafy.b102.backend.gameresult.repository.ParticipantRepository;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.ssafy.b102.backend.gameresult.event.GameResultSubmittedEvent;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;

@Service
public class GameResultService {

	private static final String USER_KEY_PREFIX = "USER:";

	private final GameResultRepository gameResultRepository;
	private final ParticipantRepository participantRepository;
	private final GameService gameService;
	private final GuestSessionService guestSessionService;
	private final ApplicationEventPublisher eventPublisher;

	public GameResultService(
		GameResultRepository gameResultRepository,
		ParticipantRepository participantRepository,
		GameService gameService,
		GuestSessionService guestSessionService,
		ApplicationEventPublisher eventPublisher
	) {
		this.gameResultRepository = gameResultRepository;
		this.participantRepository = participantRepository;
		this.gameService = gameService;
		this.guestSessionService = guestSessionService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public SubmitGameResultResponse submit(String participantKey, SubmitGameResultRequest request) {
		if (gameResultRepository.existsByPlayId(request.playId())) {
			throw new BusinessException(GameResultErrorCode.DUPLICATE_RESULT);
		}

		Game game = gameService.findGame(request.gameId())
			.orElseThrow(() -> new BusinessException(GameResultErrorCode.GAME_NOT_FOUND));

		validatePlayPeriod(request);
		validateParticipants(request.participants());
		ParticipantResultRequest requester = findRequester(participantKey, request.participants());

		GameResult gameResult = GameResult.of(
			request.playId(),
			game,
			request.gameResult(),
			request.startedAt(),
			request.endedAt()
		);
		request.participants().forEach(participant ->
			gameResult.addParticipant(toParticipant(participant, request.gameResult())));

		NewRecord newRecord = evaluateNewRecord(requester, request.gameResult(), game.getId());

		GameResult saved = gameResultRepository.save(gameResult);

		// 결과 저장 후 랭킹 변동 갱신은 이벤트로 위임한다(ranking 도메인 리스너가 커밋 후 처리).
		eventPublisher.publishEvent(new GameResultSubmittedEvent(
			game.getGameName(),
			game.getPlayMode(),
			memberUserIds(request.participants())
		));

		return SubmitGameResultResponse.of(
			saved.getId(),
			newRecord.isNewRecord(),
			newRecord.previousBestScore()
		);
	}

	/** 참가자 중 회원(USER)의 userId 집합. 랭킹 변동은 회원만 대상이다. */
	private Set<Long> memberUserIds(List<ParticipantResultRequest> participants) {
		return participants.stream()
			.filter(participant -> participant.participantType() == ParticipantType.USER)
			.map(participant -> resolveUserId(participant.participantKey()))
			.collect(Collectors.toSet());
	}

	/**
	 * 제출자의 이번 점수를 같은 게임에서의 이전 개인 최고 점수와 비교해 신기록 여부를 판정한다.
	 *
	 * <p>회원이 아니거나 이번 결과에 점수가 없으면 판정 대상이 아니다(신기록 아님, 이전 기록 없음).
	 * 최고 점수 조회는 저장 이전에 수행하므로 이번 결과는 비교 대상에 포함되지 않는다.
	 * 이전 기록이 없으면 신기록이고, 있으면 이번 점수가 엄격히 클 때만 신기록이다(동점은 아님).
	 */
	private NewRecord evaluateNewRecord(
		ParticipantResultRequest requester,
		Map<String, Object> gameResult,
		Long gameId
	) {
		if (requester.participantType() != ParticipantType.USER) {
			return NewRecord.none();
		}

		Long currentScore = extractScore(gameResult, requester.slotNo());
		if (currentScore == null) {
			return NewRecord.none();
		}

		Long userId = resolveUserId(requester.participantKey());
		Long previousBestScore = participantRepository.findBestScore(userId, gameId).orElse(null);
		boolean isNewRecord = previousBestScore == null || currentScore > previousBestScore;

		return new NewRecord(isNewRecord, previousBestScore);
	}

	/**
	 * 신기록 판정 결과. {@code previousBestScore}는 이전 기록이 없으면 {@code null}이다.
	 */
	private record NewRecord(boolean isNewRecord, Long previousBestScore) {

		private static NewRecord none() {
			return new NewRecord(false, null);
		}
	}

	private void validatePlayPeriod(SubmitGameResultRequest request) {
		if (request.endedAt().isBefore(request.startedAt())) {
			throw new BusinessException(GameResultErrorCode.INVALID_PLAY_PERIOD);
		}
	}

	private void validateParticipants(List<ParticipantResultRequest> participants) {
		Set<Integer> slotNumbers = participants.stream()
			.map(ParticipantResultRequest::slotNo)
			.collect(Collectors.toSet());
		if (slotNumbers.size() != participants.size()) {
			throw new BusinessException(GameResultErrorCode.INVALID_PARTICIPANTS);
		}

		boolean rankOutOfRange = participants.stream()
			.anyMatch(participant -> participant.rank() > participants.size());
		if (rankOutOfRange) {
			throw new BusinessException(GameResultErrorCode.INVALID_PARTICIPANTS);
		}
	}

	private ParticipantResultRequest findRequester(
		String participantKey,
		List<ParticipantResultRequest> participants
	) {
		return participants.stream()
			.filter(participant -> participant.participantKey().equals(participantKey))
			.findFirst()
			.orElseThrow(() -> new BusinessException(GameResultErrorCode.REQUESTER_NOT_PARTICIPANT));
	}

	/**
	 * 회원은 {@code user_id}를, 게스트는 검증된 세션의 닉네임을 채운다.
	 *
	 * <p>게스트는 {@code GUEST:{uuid}}를 파싱해 {@link GuestSessionService}로 Redis 세션의 존재·만료를
	 * 검증한다. 실패하면 예외가 나가 {@code @Transactional}이 결과·참가자 저장을 통째로 롤백한다.
	 * 표시 이름은 요청 body를 신뢰하지 않고 검증된 세션의 닉네임을 쓴다.
	 */
	private Participant toParticipant(
		ParticipantResultRequest request,
		Map<String, Object> gameResult
	) {
		Long userId = null;
		String displayName = request.displayName();

		if (request.participantType() == ParticipantType.USER) {
			userId = resolveUserId(request.participantKey());
		} else if (request.participantType() == ParticipantType.GUEST) {
			displayName = validateGuest(request.participantKey()).nickname();
		}

		return Participant.of(
			userId,
			request.participantType(),
			request.slotNo(),
			request.outcome(),
			request.rank(),
			displayName,
			extractScore(gameResult, request.slotNo())
		);
	}

	/**
	 * 통일 구조 {@code {"<slot>": {"score": N}}}에서 해당 슬롯의 score를 뽑는다.
	 * 값이 없거나 숫자가 아니면 null(랭킹·전적에서 제외)이다.
	 */
	private Long extractScore(Map<String, Object> gameResult, Integer slotNo) {
		if (gameResult == null || slotNo == null) {
			return null;
		}
		Object slot = gameResult.get(String.valueOf(slotNo));
		if (!(slot instanceof Map<?, ?> slotMap)) {
			return null;
		}
		Object score = slotMap.get("score");
		return score instanceof Number number ? number.longValue() : null;
	}

	private Long resolveUserId(String participantKey) {
		if (!participantKey.startsWith(USER_KEY_PREFIX)) {
			throw new BusinessException(GameResultErrorCode.INVALID_PARTICIPANTS);
		}

		try {
			return Long.parseLong(participantKey.substring(USER_KEY_PREFIX.length()));
		} catch (NumberFormatException exception) {
			throw new BusinessException(GameResultErrorCode.INVALID_PARTICIPANTS);
		}
	}

	private GuestSession validateGuest(String participantKey) {
		UUID guestSessionId = GuestParticipantKey.parse(participantKey).guestSessionId();

		return guestSessionService.validate(guestSessionId);
	}
}
