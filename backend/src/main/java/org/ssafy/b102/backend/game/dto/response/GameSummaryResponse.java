package org.ssafy.b102.backend.game.dto.response;

import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;

public record GameSummaryResponse(
	Long gameId,
	GameName gameName,
	PlayMode playMode
) {

	public static GameSummaryResponse from(Game game) {
		return new GameSummaryResponse(game.getId(), game.getGameName(), game.getPlayMode());
	}
}
