package org.ssafy.b102.backend.gameresult.dto.response;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.gameresult.entity.GameResult;
import org.ssafy.b102.backend.gameresult.entity.Participant;

public record GameResultDetailResponse(
	Long resultId,
	GameName gameName,
	PlayMode playMode,
	Integer difficulty,
	Instant startedAt,
	Instant endedAt,
	Integer mySlotNo,
	List<ParticipantResultResponse> participants,
	Map<String, Object> gameResult
) {

	public GameResultDetailResponse {
		participants = participants == null ? List.of() : List.copyOf(participants);
	}

	/**
	 * @param mySlotNo 조회자 본인의 슬롯 번호. 프론트가 내 점수·상대 목록을 구분하는 데 쓴다.
	 */
	public static GameResultDetailResponse from(GameResult gameResult, Integer mySlotNo) {
		Game game = gameResult.getGame();

		return new GameResultDetailResponse(
			gameResult.getId(),
			game.getGameName(),
			game.getPlayMode(),
			game.getDifficulty(),
			gameResult.getStartedAt(),
			gameResult.getEndedAt(),
			mySlotNo,
			gameResult.getParticipants().stream()
				.sorted(Comparator.comparing(Participant::getSlotNo))
				.map(ParticipantResultResponse::from)
				.toList(),
			gameResult.getGameResult()
		);
	}
}
