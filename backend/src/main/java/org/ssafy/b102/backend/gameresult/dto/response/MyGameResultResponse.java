package org.ssafy.b102.backend.gameresult.dto.response;

import java.time.Instant;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.gameresult.entity.GameResult;
import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.Participant;

/**
 * 내 경기 기록 목록의 한 건.
 *
 * <p>점수는 {@code gameResult} JSONB 안에 있고 게임마다 필드가 달라 목록에는 담지 않는다.
 * 상세 조회에서 확인한다.
 */
public record MyGameResultResponse(
	Long resultId,
	GameName gameName,
	PlayMode playMode,
	Integer difficulty,
	Outcome myOutcome,
	Integer myRank,
	Instant playedAt
) {

	public static MyGameResultResponse from(Participant myParticipant) {
		GameResult gameResult = myParticipant.getGameResult();
		Game game = gameResult.getGame();

		return new MyGameResultResponse(
			gameResult.getId(),
			game.getGameName(),
			game.getPlayMode(),
			game.getDifficulty(),
			myParticipant.getOutcome(),
			myParticipant.getRankNo(),
			gameResult.getEndedAt()
		);
	}
}
