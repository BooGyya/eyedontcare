package org.ssafy.b102.backend.gameresult.service;

import java.util.List;
import java.util.Set;
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

@Service
public class GameResultService {

	private static final String USER_KEY_PREFIX = "USER:";

	private final GameResultRepository gameResultRepository;
	private final GameService gameService;

	public GameResultService(GameResultRepository gameResultRepository, GameService gameService) {
		this.gameResultRepository = gameResultRepository;
		this.gameService = gameService;
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
		request.participants().forEach(participant -> gameResult.addParticipant(toParticipant(participant)));

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

	private Participant toParticipant(ParticipantResultRequest request) {
		return Participant.of(
			resolveUserId(request),
			request.participantType(),
			request.slotNo(),
			request.outcome(),
			request.rank(),
			request.displayName()
		);
	}

	/**
	 * 회원 참가자만 {@code user_id}를 가진다. 게스트와 AI는 {@code null}이다.
	 */
	private Long resolveUserId(ParticipantResultRequest request) {
		if (request.participantType() != ParticipantType.USER) {
			return null;
		}

		String participantKey = request.participantKey();
		if (!participantKey.startsWith(USER_KEY_PREFIX)) {
			throw new BusinessException(GameResultErrorCode.INVALID_PARTICIPANTS);
		}

		try {
			return Long.parseLong(participantKey.substring(USER_KEY_PREFIX.length()));
		} catch (NumberFormatException exception) {
			throw new BusinessException(GameResultErrorCode.INVALID_PARTICIPANTS);
		}
	}
}
