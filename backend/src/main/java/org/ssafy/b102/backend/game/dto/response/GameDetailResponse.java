package org.ssafy.b102.backend.game.dto.response;

import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;

public record GameDetailResponse(
	Long gameId,
	GameName gameName,
	PlayMode playMode,
	Integer difficulty
) {

	public static GameDetailResponse from(Game game) {
		return new GameDetailResponse(
			game.getId(),
			game.getGameName(),
			game.getPlayMode(),
			game.getDifficulty()
		);
	}
}
