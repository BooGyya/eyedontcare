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
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;

@Service
public class GameResultService {

	private static final String USER_KEY_PREFIX = "USER:";

	private final GameResultRepository gameResultRepository;
	private final GameService gameService;
	private final GuestSessionService guestSessionService;

	public GameResultService(
		GameResultRepository gameResultRepository,
		GameService gameService,
		GuestSessionService guestSessionService
	) {
		this.gameResultRepository = gameResultRepository;
		this.gameService = gameService;
		this.guestSessionService = guestSessionService;
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
		validateRequesterIsParticipant(participantKey, request.participants());

		GameResult gameResult = GameResult.of(
			request.playId(),
			game,
			request.gameResult(),
			request.startedAt(),
			request.endedAt()
		);
		request.participants().forEach(participant ->
			gameResult.addParticipant(toParticipant(participant, request.gameResult())));

		return SubmitGameResultResponse.from(gameResultRepository.save(gameResult));
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

	private void validateRequesterIsParticipant(
		String participantKey,
		List<ParticipantResultRequest> participants
	) {
		boolean requesterIncluded = participants.stream()
			.anyMatch(participant -> participant.participantKey().equals(participantKey));
		if (!requesterIncluded) {
			throw new BusinessException(GameResultErrorCode.REQUESTER_NOT_PARTICIPANT);
		}
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
