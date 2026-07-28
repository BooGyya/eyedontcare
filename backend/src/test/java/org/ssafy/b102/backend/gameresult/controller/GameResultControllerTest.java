package org.ssafy.b102.backend.gameresult.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.gameresult.dto.request.SubmitGameResultRequest;
import org.ssafy.b102.backend.gameresult.dto.response.SubmitGameResultResponse;
import org.ssafy.b102.backend.gameresult.exception.GameResultErrorCode;
import org.ssafy.b102.backend.gameresult.service.GameResultService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;

class GameResultControllerTest {

	private static final long CREATED_RESULT_ID = 5001L;
	private static final String PARTICIPANT_KEY_HEADER = "X-Participant-Key";
	private static final String REQUESTER_KEY = "USER:1";

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

	@Test
	void submitReturnsCreatedWithResultId() throws Exception {
		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("RESULT_SUBMITTED"))
			.andExpect(jsonPath("$.message").value("게임 결과가 저장되었습니다."))
			.andExpect(jsonPath("$.data.resultId").value(CREATED_RESULT_ID));
	}

	@Test
	void submitReturnsConflictWhenPlayIdIsDuplicated() throws Exception {
		mockMvc(new ThrowingGameResultService(GameResultErrorCode.DUPLICATE_RESULT))
			.perform(post("/api/v1/game-results")
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GAMERESULT-001"));
	}

	@Test
	void submitReturnsNotFoundWhenGameDoesNotExist() throws Exception {
		mockMvc(new ThrowingGameResultService(GameResultErrorCode.GAME_NOT_FOUND))
			.perform(post("/api/v1/game-results")
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GAMERESULT-002"));
	}

	@Test
	void submitReturnsBadRequestWhenParticipantKeyHeaderIsMissing() throws Exception {
		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isBadRequest());
	}

	@Test
	void submitReturnsBadRequestWhenParticipantsAreEmpty() throws Exception {
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
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void submitReturnsBadRequestWhenPlayIdIsMissing() throws Exception {
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
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void submitReturnsBadRequestWhenPlayPeriodIsMissing() throws Exception {
		String body = """
			{
			  "playId": "019abcde-1234-4abc-8def-0123456789ab",
			  "gameId": 1,
			  "participants": [
			    { "participantKey": "USER:1", "participantType": "USER", "slotNo": 1,
			      "displayName": "A", "outcome": "WIN", "rank": 1 }
			  ],
			  "gameResult": { "durationMs": 60000 }
			}
			""";

		mockMvc(new StubGameResultService())
			.perform(post("/api/v1/game-results")
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void submitReturnsBadRequestWhenGameResultIsMissing() throws Exception {
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
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	private MockMvc mockMvc(GameResultService gameResultService) {
		return MockMvcBuilders
			.standaloneSetup(new GameResultController(gameResultService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	private static class StubGameResultService extends GameResultService {

		private StubGameResultService() {
			super(null, null);
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
