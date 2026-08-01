package org.ssafy.b102.backend.game.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.game.GameSuccessCode;
import org.ssafy.b102.backend.game.dto.response.GameDetailResponse;
import org.ssafy.b102.backend.game.dto.response.GameListResponse;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.global.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/games")
public class GameController {

	private final GameService gameService;

	public GameController(GameService gameService) {
		this.gameService = gameService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<GameListResponse>> getGames() {
		GameListResponse response = gameService.getGames();

		return ResponseEntity.ok(ApiResponse.success(GameSuccessCode.GAME_LIST_FOUND, response));
	}

	@GetMapping("/{gameId}")
	public ResponseEntity<ApiResponse<GameDetailResponse>> getGame(@PathVariable Long gameId) {
		GameDetailResponse response = gameService.getGame(gameId);

		return ResponseEntity.ok(ApiResponse.success(GameSuccessCode.GAME_FOUND, response));
	}
}
