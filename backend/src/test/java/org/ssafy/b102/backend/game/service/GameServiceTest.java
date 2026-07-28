package org.ssafy.b102.backend.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.ssafy.b102.backend.game.dto.response.GameDetailResponse;
import org.ssafy.b102.backend.game.dto.response.GameListResponse;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.game.exception.GameErrorCode;
import org.ssafy.b102.backend.game.repository.GameRepository;
import org.ssafy.b102.backend.global.config.JpaAuditingConfig;
import org.ssafy.b102.backend.global.error.BusinessException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, GameService.class})
@EntityScan(basePackageClasses = Game.class)
@EnableJpaRepositories(basePackageClasses = GameRepository.class)
class GameServiceTest {

	@Autowired
	private GameService gameService;

	@Autowired
	private GameRepository gameRepository;

	@BeforeEach
	void setUp() {
		gameRepository.deleteAll();
	}

	@Test
	void getGamesReturnsEveryRegisteredGame() {
		gameRepository.saveAndFlush(Game.of(GameName.EYEFIGHT, PlayMode.MULTI, 2));
		gameRepository.saveAndFlush(Game.of(GameName.BLINK, PlayMode.SOLO, 1));

		GameListResponse response = gameService.getGames();

		assertThat(response.games()).hasSize(2);
		assertThat(response.games())
			.extracting(game -> game.gameName())
			.containsExactlyInAnyOrder(GameName.EYEFIGHT, GameName.BLINK);
	}

	@Test
	void getGamesReturnsEmptyListWhenNoGameExists() {
		GameListResponse response = gameService.getGames();

		assertThat(response.games()).isEmpty();
	}

	@Test
	void getGameReturnsDetailOfRequestedGame() {
		Game saved = gameRepository.saveAndFlush(Game.of(GameName.EYEFIGHT, PlayMode.MULTI, 3));

		GameDetailResponse response = gameService.getGame(saved.getId());

		assertThat(response.gameId()).isEqualTo(saved.getId());
		assertThat(response.gameName()).isEqualTo(GameName.EYEFIGHT);
		assertThat(response.playMode()).isEqualTo(PlayMode.MULTI);
		assertThat(response.difficulty()).isEqualTo(3);
	}

	@Test
	void getGameAllowsNullDifficulty() {
		Game saved = gameRepository.saveAndFlush(Game.of(GameName.DRAWING, PlayMode.SOLO, null));

		GameDetailResponse response = gameService.getGame(saved.getId());

		assertThat(response.difficulty()).isNull();
	}

	@Test
	void getGameThrowsBusinessExceptionWhenGameDoesNotExist() {
		assertThatThrownBy(() -> gameService.getGame(-1L))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameErrorCode.GAME_NOT_FOUND));
	}
}
