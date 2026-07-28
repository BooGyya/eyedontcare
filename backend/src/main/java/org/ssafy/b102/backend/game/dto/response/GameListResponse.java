package org.ssafy.b102.backend.game.dto.response;

import java.util.List;
import org.ssafy.b102.backend.game.entity.Game;

public record GameListResponse(List<GameSummaryResponse> games) {

	public GameListResponse {
		games = games == null ? List.of() : List.copyOf(games);
	}

	public static GameListResponse from(List<Game> games) {
		return new GameListResponse(games.stream().map(GameSummaryResponse::from).toList());
	}
}
