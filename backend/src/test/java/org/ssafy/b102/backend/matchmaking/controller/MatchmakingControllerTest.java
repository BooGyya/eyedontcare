package org.ssafy.b102.backend.matchmaking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.matchmaking.dto.response.MatchStatusResponse;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;

class MatchmakingControllerTest {

	private static final String PARTICIPANT_KEY_HEADER = "X-Participant-Key";
	private static final String REQUESTER_KEY = "USER:1";
	private static final String JOIN_PATH = "/api/v1/match/join";
	private static final String CANCEL_PATH = "/api/v1/match/cancel";
	private static final String JOIN_BODY = "{\"gameType\":\"EYEFIGHT\"}";
	private static final Instant QUEUED_AT = Instant.parse("2026-07-29T09:00:00Z");
	private static final UUID ROOM_ID = UUID.fromString("019abcde-1234-4abc-8def-0123456789ab");

	@Test
	void joinReturnsQueuedStatus() throws Exception {
		mockMvc(new StubService(MatchStatus.SEARCHING))
			.perform(post(JOIN_PATH)
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MATCH_QUEUED"))
			.andExpect(jsonPath("$.message").value("랜덤 매칭이 접수되었습니다."))
			.andExpect(jsonPath("$.data.participantKey").value(REQUESTER_KEY))
			.andExpect(jsonPath("$.data.gameType").value("EYEFIGHT"))
			.andExpect(jsonPath("$.data.matchStatus").value("SEARCHING"))
			.andExpect(jsonPath("$.data.queuedAt").value("2026-07-29T09:00:00Z"));
	}

	/**
	 * 대기자가 있으면 신청 즉시 성사된다. 명세의 응답 필드 설명은 SEARCHING만 적혀 있으나
	 * 실제 상태를 그대로 반환한다. WebSocket 도입 전까지 클라이언트가 결과를 알 수 있는 유일한 경로다.
	 */
	@Test
	void joinReturnsEnteringRoomWhenMatchedImmediately() throws Exception {
		mockMvc(new StubService(MatchStatus.ENTERING_ROOM))
			.perform(post(JOIN_PATH)
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.matchStatus").value("ENTERING_ROOM"))
			.andExpect(jsonPath("$.data.waitingRoomId").value(ROOM_ID.toString()));
	}

	@Test
	void joinRequiresParticipantKeyHeader() throws Exception {
		mockMvc(new StubService(MatchStatus.SEARCHING))
			.perform(post(JOIN_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isBadRequest());
	}

	@Test
	void joinRejectsMissingGameType() throws Exception {
		mockMvc(new StubService(MatchStatus.SEARCHING))
			.perform(post(JOIN_PATH)
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void joinReturnsBadRequestForUnsupportedGameType() throws Exception {
		mockMvc(new ThrowingService(MatchmakingErrorCode.INVALID_GAME_TYPE))
			.perform(post(JOIN_PATH)
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameType\":\"CHESS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MATCHMAKING-002"))
			.andExpect(jsonPath("$.message").value("지원하지 않는 게임입니다."));
	}

	@Test
	void joinReturnsConflictWhenAlreadyInQueue() throws Exception {
		mockMvc(new ThrowingService(MatchmakingErrorCode.ALREADY_IN_QUEUE))
			.perform(post(JOIN_PATH)
				.header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MATCHMAKING-001"))
			.andExpect(jsonPath("$.message").value("이미 매칭 대기 중입니다."));
	}

	@Test
	void cancelReturnsCancelledStatus() throws Exception {
		mockMvc(new StubService(MatchStatus.CANCELLED))
			.perform(delete(CANCEL_PATH).header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MATCH_CANCELLED"))
			.andExpect(jsonPath("$.message").value("랜덤 매칭이 취소되었습니다."))
			.andExpect(jsonPath("$.data.matchStatus").value("CANCELLED"));
	}

	@Test
	void cancelReturnsNotFoundWhenNoRequest() throws Exception {
		mockMvc(new ThrowingService(MatchmakingErrorCode.REQUEST_NOT_FOUND))
			.perform(delete(CANCEL_PATH).header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MATCHMAKING-003"));
	}

	@Test
	void cancelReturnsConflictWhenAlreadyMatched() throws Exception {
		mockMvc(new ThrowingService(MatchmakingErrorCode.CANCEL_NOT_ALLOWED))
			.perform(delete(CANCEL_PATH).header(PARTICIPANT_KEY_HEADER, REQUESTER_KEY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MATCHMAKING-004"));
	}

	private MockMvc mockMvc(MatchmakingService matchmakingService) {
		return MockMvcBuilders
			.standaloneSetup(new MatchmakingController(matchmakingService))
			.setValidator(validator())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	private static LocalValidatorFactoryBean validator() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

		return validator;
	}

	private static class StubService extends MatchmakingService {

		private final MatchStatus matchStatus;

		private StubService(MatchStatus matchStatus) {
			super(null, null);
			this.matchStatus = matchStatus;
		}

		@Override
		public MatchStatusResponse join(String participantKey, String gameType) {
			return new MatchStatusResponse(
				participantKey,
				GameName.EYEFIGHT,
				matchStatus,
				matchStatus == MatchStatus.SEARCHING ? null : ROOM_ID,
				QUEUED_AT
			);
		}

		@Override
		public MatchStatusResponse cancel(String participantKey) {
			return new MatchStatusResponse(
				participantKey,
				GameName.EYEFIGHT,
				MatchStatus.CANCELLED,
				null,
				QUEUED_AT
			);
		}
	}

	private static final class ThrowingService extends StubService {

		private final MatchmakingErrorCode errorCode;

		private ThrowingService(MatchmakingErrorCode errorCode) {
			super(MatchStatus.SEARCHING);
			this.errorCode = errorCode;
		}

		@Override
		public MatchStatusResponse join(String participantKey, String gameType) {
			throw new BusinessException(errorCode);
		}

		@Override
		public MatchStatusResponse cancel(String participantKey) {
			throw new BusinessException(errorCode);
		}
	}
}
