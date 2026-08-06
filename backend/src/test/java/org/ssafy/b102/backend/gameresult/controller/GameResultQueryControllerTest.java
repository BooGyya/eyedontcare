package org.ssafy.b102.backend.gameresult.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.gameresult.dto.response.GameResultDetailResponse;
import org.ssafy.b102.backend.gameresult.dto.response.MyGameResultPageResponse;
import org.ssafy.b102.backend.gameresult.dto.response.MyGameResultResponse;
import org.ssafy.b102.backend.gameresult.dto.response.ParticipantResultResponse;
import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.ParticipantType;
import org.ssafy.b102.backend.gameresult.exception.GameResultErrorCode;
import org.ssafy.b102.backend.gameresult.service.GameResultQueryService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;

class GameResultQueryControllerTest {

	private static final String PARTICIPANT_KEY_HEADER = "X-Participant-Key";
	private static final Long USER_ID = 1L;
	private static final long RESULT_ID = 5001L;
	private static final Instant PLAYED_AT = Instant.parse("2026-07-28T09:03:00Z");

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getMyResultsReturnsPagedRecords() throws Exception {
		StubQueryService queryService = new StubQueryService();

		mockMvc(queryService)
			.perform(get("/api/v1/game-results/me")
				.header(PARTICIPANT_KEY_HEADER, "USER:999"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("RESULT_LIST_FOUND"))
			.andExpect(jsonPath("$.message").value("경기 기록 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.size").value(10))
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.content[0].resultId").value(RESULT_ID))
			.andExpect(jsonPath("$.data.content[0].gameName").value("HOCKEY"))
			.andExpect(jsonPath("$.data.content[0].playMode").value("RANDOM"))
			.andExpect(jsonPath("$.data.content[0].myOutcome").value("WIN"))
			.andExpect(jsonPath("$.data.content[0].myRank").value(1));

		org.assertj.core.api.Assertions.assertThat(
			queryService.requestedUserId
		).isEqualTo(USER_ID);
	}

	@Test
	void getMyResultsAcceptsPageAndSize() throws Exception {
		mockMvc(new StubQueryService())
			.perform(get("/api/v1/game-results/me")
				.param("page", "2")
				.param("size", "5"))
			.andExpect(status().isOk());
	}

	@Test
	void getMyResultsRejectsPageBelowOne() throws Exception {
		mockMvc(new StubQueryService())
			.perform(get("/api/v1/game-results/me")
				.param("page", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void getResultReturnsDetail() throws Exception {
		mockMvc(new StubQueryService())
			.perform(get("/api/v1/game-results/{resultId}", RESULT_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("RESULT_FOUND"))
			.andExpect(jsonPath("$.message").value("경기 기록을 조회했습니다."))
			.andExpect(jsonPath("$.data.resultId").value(RESULT_ID))
			.andExpect(jsonPath("$.data.gameName").value("HOCKEY"))
			.andExpect(jsonPath("$.data.mySlotNo").value(1))
			.andExpect(jsonPath("$.data.participants[0].slotNo").value(1))
			.andExpect(jsonPath("$.data.participants[0].participantType").value("USER"))
			.andExpect(jsonPath("$.data.participants[0].displayName").value("A"))
			.andExpect(jsonPath("$.data.participants[0].score").value(5))
			.andExpect(jsonPath("$.data.gameResult.durationMs").value(60000));
	}

	@Test
	void getResultReturnsNotFoundWhenResultDoesNotExist() throws Exception {
		mockMvc(new ThrowingQueryService(GameResultErrorCode.RESULT_NOT_FOUND))
			.perform(get("/api/v1/game-results/{resultId}", RESULT_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GAMERESULT-007"));
	}

	@Test
	void getResultReturnsForbiddenWhenRequesterDidNotParticipate() throws Exception {
		mockMvc(new ThrowingQueryService(GameResultErrorCode.RESULT_ACCESS_DENIED))
			.perform(get("/api/v1/game-results/{resultId}", RESULT_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("GAMERESULT-008"));
	}

	private MockMvc mockMvc(GameResultQueryService queryService) {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(
				new AuthenticatedUser(USER_ID),
				null,
				List.of()
			)
		);

		return MockMvcBuilders
			.standaloneSetup(new GameResultQueryController(queryService))
			.setCustomArgumentResolvers(
				new AuthenticationPrincipalArgumentResolver()
			)
			.setValidator(validator())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	/**
	 * {@code @RequestParam}의 Bean Validation은 Validator가 등록되어야 동작한다.
	 * 애플리케이션에서는 자동 구성되지만 standaloneSetup에는 직접 넣어야 한다.
	 */
	private static LocalValidatorFactoryBean validator() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

		return validator;
	}

	private static class StubQueryService extends GameResultQueryService {

		private Long requestedUserId;

		private StubQueryService() {
			super(null, null);
		}

		@Override
		public MyGameResultPageResponse getMyResults(Long userId, int page, int size) {
			requestedUserId = userId;

			return new MyGameResultPageResponse(
				List.of(new MyGameResultResponse(
					RESULT_ID, GameName.HOCKEY, PlayMode.RANDOM, null, Outcome.WIN, 1, PLAYED_AT
				)),
				1,
				10,
				1
			);
		}

		@Override
		public GameResultDetailResponse getResult(Long userId, Long resultId) {
			requestedUserId = userId;

			return new GameResultDetailResponse(
				RESULT_ID,
				GameName.HOCKEY,
				PlayMode.RANDOM,
				null,
				Instant.parse("2026-07-28T09:00:00Z"),
				PLAYED_AT,
				1,
				List.of(
					new ParticipantResultResponse(1, ParticipantType.USER, "A", Outcome.WIN, 1, 5L),
					new ParticipantResultResponse(2, ParticipantType.USER, "B", Outcome.LOSE, 2, 3L)
				),
				Map.of("durationMs", 60000)
			);
		}
	}

	private static final class ThrowingQueryService extends StubQueryService {

		private final GameResultErrorCode errorCode;

		private ThrowingQueryService(GameResultErrorCode errorCode) {
			this.errorCode = errorCode;
		}

		@Override
		public MyGameResultPageResponse getMyResults(Long userId, int page, int size) {
			throw new BusinessException(errorCode);
		}

		@Override
		public GameResultDetailResponse getResult(Long userId, Long resultId) {
			throw new BusinessException(errorCode);
		}
	}
}
