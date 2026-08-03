package org.ssafy.b102.backend.ranking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.ranking.dto.response.GameRankingResponse;
import org.ssafy.b102.backend.ranking.dto.response.GameRankingSummary;
import org.ssafy.b102.backend.ranking.dto.response.MyRank;
import org.ssafy.b102.backend.ranking.dto.response.RankingEntry;
import org.ssafy.b102.backend.ranking.dto.response.RankingListResponse;
import org.ssafy.b102.backend.ranking.exception.RankingErrorCode;
import org.ssafy.b102.backend.ranking.service.RankingService;
import org.ssafy.b102.backend.ranking.support.RankType;

class RankingControllerTest {

	private static final Long USER_ID = 1L;
	private static final LocalDate WEEK_START = LocalDate.of(2026, 8, 3);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 홈_요약_랭킹을_조회한다() throws Exception {
		StubRankingService service = new StubRankingService();
		service.listResponse = new RankingListResponse(
			"weekly", WEEK_START, List.of(new GameRankingSummary(
				GameName.BLINK, RankType.BEST_SCORE, "count",
				List.of(new RankingEntry(1, 7L, "방울반짝", 128, null)),
				new MyRank(3, 103)))
		);

		mockMvc(service)
			.perform(get("/api/v1/rankings").param("limit", "3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("RANKING_LIST_FOUND"))
			.andExpect(jsonPath("$.data.weekStart").value("2026-08-03"))
			.andExpect(jsonPath("$.data.games[0].gameName").value("BLINK"))
			.andExpect(jsonPath("$.data.games[0].top[0].value").value(128))
			.andExpect(jsonPath("$.data.games[0].top[0].achievedAt").doesNotExist())
			.andExpect(jsonPath("$.data.games[0].myRank.rank").value(3));

		assertThat(service.capturedLimit).isEqualTo(3);
	}

	@Test
	void 게임별_랭킹을_조회한다() throws Exception {
		StubRankingService service = new StubRankingService();
		service.gameResponse = new GameRankingResponse(
			GameName.BLINK, RankType.BEST_SCORE, "count", "weekly", WEEK_START,
			List.of(new RankingEntry(
				1, 7L, "방울반짝", 128, Instant.parse("2026-08-05T01:00:00Z"))),
			new MyRank(3, 103), 1, 20, 1, 1
		);

		mockMvc(service)
			.perform(get("/api/v1/rankings/{gameName}", "BLINK"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GAME_RANKING_FOUND"))
			.andExpect(jsonPath("$.data.gameName").value("BLINK"))
			.andExpect(jsonPath("$.data.rankType").value("BEST_SCORE"))
			.andExpect(jsonPath("$.data.rankings[0].nickname").value("방울반짝"))
			.andExpect(jsonPath("$.data.myRank.value").value(103));

		assertThat(service.capturedGameName).isEqualTo("BLINK");
	}

	@Test
	void 지원하지_않는_게임은_400_RANKING_001이다() throws Exception {
		StubRankingService service = new StubRankingService();
		service.toThrow = new BusinessException(RankingErrorCode.INVALID_GAME);

		mockMvc(service)
			.perform(get("/api/v1/rankings/{gameName}", "CHESS"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RANKING-001"));
	}

	private MockMvc mockMvc(RankingService service) {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(
				new AuthenticatedUser(USER_ID), null, List.of()
			)
		);

		return MockMvcBuilders
			.standaloneSetup(new RankingController(service))
			.setCustomArgumentResolvers(
				new AuthenticationPrincipalArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	private static class StubRankingService extends RankingService {

		private BusinessException toThrow;
		private RankingListResponse listResponse;
		private GameRankingResponse gameResponse;
		private int capturedLimit;
		private String capturedGameName;

		private StubRankingService() {
			super(null, null);
		}

		@Override
		public RankingListResponse getRankings(Long userId, int limit) {
			if (toThrow != null) {
				throw toThrow;
			}
			this.capturedLimit = limit;
			return listResponse;
		}

		@Override
		public GameRankingResponse getGameRanking(
			Long userId, String gameName, int page, int size
		) {
			if (toThrow != null) {
				throw toThrow;
			}
			this.capturedGameName = gameName;
			return gameResponse;
		}
	}
}
