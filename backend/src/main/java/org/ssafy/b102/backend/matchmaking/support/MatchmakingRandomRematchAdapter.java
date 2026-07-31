package org.ssafy.b102.backend.matchmaking.support;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.service.MatchNotifier;
import org.ssafy.b102.backend.matchmaking.service.RandomRematchService;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequester;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequestResult;

@Component
public class MatchmakingRandomRematchAdapter implements RandomRematchRequester {

	private static final Logger log = LoggerFactory.getLogger(MatchmakingRandomRematchAdapter.class);

	private final RandomRematchService randomRematchService;
	private final MatchNotifier matchNotifier;

	public MatchmakingRandomRematchAdapter(
		RandomRematchService randomRematchService,
		MatchNotifier matchNotifier
	) {
		this.randomRematchService = randomRematchService;
		this.matchNotifier = matchNotifier;
	}

	@Override
	public RandomRematchRequestResult requeueRemaining(
		UUID previousRoomId,
		GameName gameName,
		String participantKey
	) {
		RandomRematchRequestResult result;
		try {
			result = randomRematchService.requeueRemaining(previousRoomId, gameName, participantKey);
		} catch (RuntimeException exception) {
			log.error(
				"자동 재매칭 등록에 실패했습니다. previousRoomId={}, participantKey={}",
				previousRoomId,
				participantKey,
				exception
			);
			notifyError(participantKey, MatchmakingErrorCode.REMATCH_FAILED);
			return RandomRematchRequestResult.FAILED;
		}

		switch (result) {
			case REQUEUED -> notifyRequeued(participantKey, gameName);
			case PARTICIPANT_INVALID ->
				notifyError(participantKey, MatchmakingErrorCode.REMATCH_PARTICIPANT_INVALID);
			case FAILED -> notifyError(participantKey, MatchmakingErrorCode.REMATCH_FAILED);
			case ALREADY_REQUEUED -> {
				// 동일 요청은 Redis 상태와 WebSocket 알림을 모두 반복하지 않는다.
			}
		}
		return result;
	}

	private void notifyRequeued(String participantKey, GameName gameName) {
		try {
			matchNotifier.notifyRequeued(participantKey, gameName);
		} catch (RuntimeException exception) {
			log.warn("재매칭 등록 알림 전송에 실패했습니다. participantKey={}", participantKey, exception);
		}
	}

	private void notifyError(String participantKey, MatchmakingErrorCode errorCode) {
		try {
			matchNotifier.notifyError(participantKey, errorCode);
		} catch (RuntimeException exception) {
			log.warn("재매칭 오류 알림 전송에 실패했습니다. participantKey={}", participantKey, exception);
		}
	}
}
