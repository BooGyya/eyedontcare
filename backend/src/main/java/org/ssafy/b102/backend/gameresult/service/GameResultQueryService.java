package org.ssafy.b102.backend.gameresult.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.gameresult.dto.response.GameResultDetailResponse;
import org.ssafy.b102.backend.gameresult.dto.response.MyGameResultPageResponse;
import org.ssafy.b102.backend.gameresult.entity.GameResult;
import org.ssafy.b102.backend.gameresult.entity.Participant;
import org.ssafy.b102.backend.gameresult.exception.GameResultErrorCode;
import org.ssafy.b102.backend.gameresult.repository.GameResultRepository;
import org.ssafy.b102.backend.gameresult.repository.ParticipantRepository;
import org.ssafy.b102.backend.global.error.BusinessException;

/**
 * 경기 기록 조회 서비스.
 *
 * <p>제출은 쓰기 트랜잭션, 조회는 읽기 전용이라 {@link GameResultService}와 분리했다.
 * 게스트는 {@code user_id}가 없어 자신의 기록을 특정할 수 없으므로 회원만 조회할 수 있다.
 */
@Service
public class GameResultQueryService {

	private static final String USER_KEY_PREFIX = "USER:";

	private final GameResultRepository gameResultRepository;
	private final ParticipantRepository participantRepository;

	public GameResultQueryService(
		GameResultRepository gameResultRepository,
		ParticipantRepository participantRepository
	) {
		this.gameResultRepository = gameResultRepository;
		this.participantRepository = participantRepository;
	}

	@Transactional(readOnly = true)
	public MyGameResultPageResponse getMyResults(String participantKey, int page, int size) {
		Long userId = requireUserId(participantKey);
		Pageable pageable = PageRequest.of(page - 1, size);

		Page<Participant> myResults = participantRepository.findMyResults(userId, pageable);

		return MyGameResultPageResponse.from(myResults);
	}

	@Transactional(readOnly = true)
	public GameResultDetailResponse getResult(String participantKey, Long resultId) {
		Long userId = requireUserId(participantKey);

		GameResult gameResult = gameResultRepository.findById(resultId)
			.orElseThrow(() -> new BusinessException(GameResultErrorCode.RESULT_NOT_FOUND));

		participantRepository.findByResultIdAndUserId(resultId, userId)
			.orElseThrow(() -> new BusinessException(GameResultErrorCode.RESULT_ACCESS_DENIED));

		return GameResultDetailResponse.from(gameResult);
	}

	/**
	 * 참가자 키에서 회원 ID를 얻는다. 게스트와 AI는 조회 대상이 아니다.
	 */
	private Long requireUserId(String participantKey) {
		if (participantKey == null || !participantKey.startsWith(USER_KEY_PREFIX)) {
			throw new BusinessException(GameResultErrorCode.MEMBER_ONLY);
		}

		try {
			return Long.parseLong(participantKey.substring(USER_KEY_PREFIX.length()));
		} catch (NumberFormatException exception) {
			throw new BusinessException(GameResultErrorCode.MEMBER_ONLY);
		}
	}
}
