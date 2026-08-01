package org.ssafy.b102.backend.game.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.game.dto.response.GameDetailResponse;
import org.ssafy.b102.backend.game.dto.response.GameListResponse;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
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

	/**
	 * 다른 도메인이 게임을 참조할 때 사용하는 조회 메서드다.
	 *
	 * <p>도메인마다 게임이 없을 때 반환할 오류가 다르므로 예외를 던지지 않고
	 * {@link Optional}을 반환한다. 호출하는 도메인이 자신의 오류 코드로 처리한다.
	 */
	@Transactional(readOnly = true)
	public Optional<Game> findGame(Long gameId) {
		return gameRepository.findById(gameId);
	}

	@Transactional(readOnly = true)
	public boolean supportsPlayMode(GameName gameName, PlayMode playMode) {
		return gameRepository.existsByGameNameAndPlayMode(gameName, playMode);
	}
}
