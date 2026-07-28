package org.ssafy.b102.backend.game.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.game.dto.response.GameDetailResponse;
import org.ssafy.b102.backend.game.dto.response.GameListResponse;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.exception.GameErrorCode;
import org.ssafy.b102.backend.game.repository.GameRepository;
import org.ssafy.b102.backend.global.error.BusinessException;

@Service
public class GameService {

	private final GameRepository gameRepository;

	public GameService(GameRepository gameRepository) {
		this.gameRepository = gameRepository;
	}

	@Transactional(readOnly = true)
	public GameListResponse getGames() {
		return GameListResponse.from(gameRepository.findAll());
	}

	@Transactional(readOnly = true)
	public GameDetailResponse getGame(Long gameId) {
		Game game = gameRepository.findById(gameId)
			.orElseThrow(() -> new BusinessException(GameErrorCode.GAME_NOT_FOUND));

		return GameDetailResponse.from(game);
	}
}
