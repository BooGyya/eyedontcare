package org.ssafy.b102.backend.game.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.game.dto.response.GameDetailResponse;
import org.ssafy.b102.backend.game.dto.response.GameListResponse;
import org.ssafy.b102.backend.game.dto.response.GameSummaryResponse;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.game.exception.GameErrorCode;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;

class GameControllerTest {

	private static final long EXISTING_GAME_ID = 1L;
	private static final long MISSING_GAME_ID = 999L;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
			.standaloneSetup(new GameController(new StubGameService()))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void getGamesReturnsWrappedGameList() throws Exception {
		mockMvc.perform(get("/api/v1/games"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GAME_LIST_FOUND"))
			.andExpect(jsonPath("$.message").value("게임 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.games[0].gameId").value(1))
			.andExpect(jsonPath("$.data.games[0].gameName").value("EYEFIGHT"))
			.andExpect(jsonPath("$.data.games[0].playMode").value("MULTI"));
	}

	@Test
	void getGameReturnsWrappedGameDetail() throws Exception {
		mockMvc.perform(get("/api/v1/games/{gameId}", EXISTING_GAME_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GAME_FOUND"))
			.andExpect(jsonPath("$.message").value("게임 상세를 조회했습니다."))
			.andExpect(jsonPath("$.data.gameId").value(1))
			.andExpect(jsonPath("$.data.gameName").value("EYEFIGHT"))
			.andExpect(jsonPath("$.data.playMode").value("MULTI"))
			.andExpect(jsonPath("$.data.difficulty").value(2));
	}

	@Test
	void getGameReturnsNotFoundWhenGameDoesNotExist() throws Exception {
		mockMvc.perform(get("/api/v1/games/{gameId}", MISSING_GAME_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GAME-001"))
			.andExpect(jsonPath("$.message").value("게임을 찾을 수 없습니다."))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void getGameReturnsBadRequestWhenGameIdIsNotNumeric() throws Exception {
		mockMvc.perform(get("/api/v1/games/{gameId}", "not-a-number"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-004"));
	}

	private static final class StubGameService extends GameService {

		private StubGameService() {
			super(null);
		}

		@Override
		public GameListResponse getGames() {
			return new GameListResponse(
				List.of(new GameSummaryResponse(EXISTING_GAME_ID, GameName.EYEFIGHT, PlayMode.MULTI))
			);
		}

		@Override
		public GameDetailResponse getGame(Long gameId) {
			if (!Long.valueOf(EXISTING_GAME_ID).equals(gameId)) {
				throw new BusinessException(GameErrorCode.GAME_NOT_FOUND);
			}

			return new GameDetailResponse(EXISTING_GAME_ID, GameName.EYEFIGHT, PlayMode.MULTI, 2);
		}
	}
}
