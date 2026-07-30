package org.ssafy.b102.backend.gameresult.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.gameresult.dto.request.SubmitGameResultRequest;
import org.ssafy.b102.backend.gameresult.dto.response.SubmitGameResultResponse;
import org.ssafy.b102.backend.gameresult.exception.GameResultErrorCode;
import org.ssafy.b102.backend.gameresult.service.GameResultService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;

class GameResultControllerTest {

	private static final long CREATED_RESULT_ID = 5001L;
	private static final Long MEMBER_USER_ID = 1L;
	private static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";
	private static final String GUEST_ID = "019abcde-5678-4abc-8def-0123456789ab";

	private static final String VALID_BODY = """
		{
		  "playId": "019abcde-1234-4abc-8def-0123456789ab",
		  "gameId": 1,
		  "startedAt": "2026-07-28T09:00:00Z",
		  "endedAt": "2026-07-28T09:01:00Z",
		  "participants": [
		    { "participantKey": "USER:1", "participantType": "USER", "slotNo": 1,
		      "displayName": "A", "outcome": "WIN", "rank": 1 },
		    { "participantKey": "USER:2", "participantType": "USER", "slotNo": 2,
		      "displayName": "B", "outcome": "LOSE", "rank": 2 }
		  ],
		  "gameResult": { "durationMs": 60000 }
		}
		""";

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void memberSubmitReturnsCreatedWithResultId() throws Exception {
		authenticateMember();

		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("RESULT_SUBMITTED"))
			.andExpect(jsonPath("$.data.resultId").value(CREATED_RESULT_ID));
	}

	@Test
	void guestSubmitWithSessionHeaderReturnsCreated() throws Exception {
		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.header(GUEST_SESSION_HEADER, GUEST_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.resultId").value(CREATED_RESULT_ID));
	}

	/**
	 * 회원 토큰도 게스트 세션 헤더도 없으면 제출자를 식별할 수 없어 거절한다.
	 */
	@Test
	void submitWithoutIdentityIsRejected() throws Exception {
		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("GUEST-001"));
	}

	@Test
	void submitReturnsConflictWhenPlayIdIsDuplicated() throws Exception {
		authenticateMember();

		mockMvc(new ThrowingGameResultService(GameResultErrorCode.DUPLICATE_RESULT))
			.perform(post("/api/v1/game-results")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GAMERESULT-001"));
	}

	@Test
	void submitReturnsNotFoundWhenGameDoesNotExist() throws Exception {
		authenticateMember();

		mockMvc(new ThrowingGameResultService(GameResultErrorCode.GAME_NOT_FOUND))
			.perform(post("/api/v1/game-results")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GAMERESULT-002"));
	}

	@Test
	void submitReturnsBadRequestWhenParticipantsAreEmpty() throws Exception {
		authenticateMember();
		String body = """
			{
			  "playId": "019abcde-1234-4abc-8def-0123456789ab",
			  "gameId": 1,
			  "startedAt": "2026-07-28T09:00:00Z",
			  "endedAt": "2026-07-28T09:01:00Z",
			  "participants": [],
			  "gameResult": { "durationMs": 60000 }
			}
			""";

		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void submitReturnsBadRequestWhenPlayIdIsMissing() throws Exception {
		authenticateMember();
		String body = """
			{
			  "gameId": 1,
			  "startedAt": "2026-07-28T09:00:00Z",
			  "endedAt": "2026-07-28T09:01:00Z",
			  "participants": [
			    { "participantKey": "USER:1", "participantType": "USER", "slotNo": 1,
			      "displayName": "A", "outcome": "WIN", "rank": 1 }
			  ],
			  "gameResult": { "durationMs": 60000 }
			}
			""";

		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void submitReturnsBadRequestWhenGameResultIsMissing() throws Exception {
		authenticateMember();
		String body = """
			{
			  "playId": "019abcde-1234-4abc-8def-0123456789ab",
			  "gameId": 1,
			  "startedAt": "2026-07-28T09:00:00Z",
			  "endedAt": "2026-07-28T09:01:00Z",
			  "participants": [
			    { "participantKey": "USER:1", "participantType": "USER", "slotNo": 1,
			      "displayName": "A", "outcome": "WIN", "rank": 1 }
			  ]
			}
			""";

		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	private static void authenticateMember() {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(
				new AuthenticatedUser(MEMBER_USER_ID),
				null,
				List.of()
			)
		);
	}

	private MockMvc mockMvc(GameResultService gameResultService) {
		return MockMvcBuilders
			.standaloneSetup(new GameResultController(gameResultService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	private static class StubGameResultService extends GameResultService {

		private StubGameResultService() {
			super(null, null, null);
		}

		@Override
		public SubmitGameResultResponse submit(String participantKey, SubmitGameResultRequest request) {
			return new SubmitGameResultResponse(CREATED_RESULT_ID);
		}
	}

	private static final class ThrowingGameResultService extends StubGameResultService {

		private final GameResultErrorCode errorCode;

		private ThrowingGameResultService(GameResultErrorCode errorCode) {
			this.errorCode = errorCode;
		}

		@Override
		public SubmitGameResultResponse submit(String participantKey, SubmitGameResultRequest request) {
			throw new BusinessException(errorCode);
		}
	}
}
