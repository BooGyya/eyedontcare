package org.ssafy.b102.backend.matchmaking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.guest.config.GuestSessionProperties;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.matchmaking.dto.response.MatchStatusResponse;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;
import org.ssafy.b102.backend.matchmaking.support.MatchParticipantResolver;

class MatchmakingControllerTest {

	private static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";
	private static final Long MEMBER_USER_ID = 1L;
	private static final String MEMBER_KEY = "USER:1";
	private static final String JOIN_PATH = "/api/v1/match/join";
	private static final String CANCEL_PATH = "/api/v1/match/cancel";
	private static final String JOIN_BODY = "{\"gameType\":\"EYEFIGHT\"}";
	private static final Instant QUEUED_AT = Instant.parse("2026-07-29T09:00:00Z");
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
	private static final UUID ROOM_ID = UUID.fromString("019abcde-1234-4abc-8def-0123456789ab");
	private static final UUID GUEST_ID = UUID.fromString("019abcde-5678-4abc-8def-0123456789ab");
	private static final String GUEST_NICKNAME = "용감한수달";

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	// --- 회원 (JWT) ---

	@Test
	void memberJoinReturnsQueuedStatus() throws Exception {
		authenticateMember();

		mockMvc(new StubService(MatchStatus.SEARCHING), new StubGuestSessionService())
			.perform(post(JOIN_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MATCH_QUEUED"))
			.andExpect(jsonPath("$.data.participantKey").value(MEMBER_KEY))
			.andExpect(jsonPath("$.data.matchStatus").value("SEARCHING"))
			.andExpect(jsonPath("$.data.queuedAt").value("2026-07-29T09:00:00Z"))
			.andExpect(jsonPath("$.data.guestSessionId").doesNotExist())
			.andExpect(jsonPath("$.data.guestNickname").doesNotExist());
	}

	@Test
	void memberJoinReturnsEnteringRoomWhenMatchedImmediately() throws Exception {
		authenticateMember();

		mockMvc(new StubService(MatchStatus.ENTERING_ROOM), new StubGuestSessionService())
			.perform(post(JOIN_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.matchStatus").value("ENTERING_ROOM"))
			.andExpect(jsonPath("$.data.waitingRoomId").value(ROOM_ID.toString()));
	}

	@Test
	void joinRejectsMissingGameType() throws Exception {
		authenticateMember();

		mockMvc(new StubService(MatchStatus.SEARCHING), new StubGuestSessionService())
			.perform(post(JOIN_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void joinReturnsBadRequestForUnsupportedGameType() throws Exception {
		authenticateMember();

		mockMvc(new ThrowingService(MatchmakingErrorCode.INVALID_GAME_TYPE), new StubGuestSessionService())
			.perform(post(JOIN_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameType\":\"CHESS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MATCHMAKING-002"));
	}

	@Test
	void joinReturnsConflictWhenAlreadyInQueue() throws Exception {
		authenticateMember();

		mockMvc(new ThrowingService(MatchmakingErrorCode.ALREADY_IN_QUEUE), new StubGuestSessionService())
			.perform(post(JOIN_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MATCHMAKING-001"));
	}

	@Test
	void memberCancelReturnsCancelledStatus() throws Exception {
		authenticateMember();

		mockMvc(new StubService(MatchStatus.CANCELLED), new StubGuestSessionService())
			.perform(delete(CANCEL_PATH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MATCH_CANCELLED"))
			.andExpect(jsonPath("$.data.matchStatus").value("CANCELLED"));
	}

	// --- 게스트 ---

	@Test
	void guestJoinWithoutSessionIssuesOneAndReturnsIt() throws Exception {
		StubGuestSessionService guest = new StubGuestSessionService();
		guest.willIssue(new GuestSession(GUEST_ID, GUEST_NICKNAME, NOW, NOW.plus(Duration.ofHours(24))));

		mockMvc(new StubService(MatchStatus.SEARCHING), guest)
			.perform(post(JOIN_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.participantKey").value("GUEST:" + GUEST_ID))
			.andExpect(jsonPath("$.data.guestSessionId").value(GUEST_ID.toString()))
			.andExpect(jsonPath("$.data.guestNickname").value(GUEST_NICKNAME));
	}

	@Test
	void guestJoinWithValidSessionReusesIt() throws Exception {
		StubGuestSessionService guest = new StubGuestSessionService();
		guest.hasExisting(new GuestSession(GUEST_ID, GUEST_NICKNAME, NOW, NOW.plus(Duration.ofHours(24))));

		mockMvc(new StubService(MatchStatus.SEARCHING), guest)
			.perform(post(JOIN_PATH)
				.header(GUEST_SESSION_HEADER, GUEST_ID.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.participantKey").value("GUEST:" + GUEST_ID))
			.andExpect(jsonPath("$.data.guestSessionId").value(GUEST_ID.toString()));
	}

	@Test
	void guestCancelWithoutSessionIdIsRejected() throws Exception {
		mockMvc(new StubService(MatchStatus.CANCELLED), new StubGuestSessionService())
			.perform(delete(CANCEL_PATH))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MATCHMAKING-005"));
	}

	// --- helpers ---

	private static void authenticateMember() {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(
				new AuthenticatedUser(MEMBER_USER_ID),
				null,
				List.of()
			)
		);
	}

	private MockMvc mockMvc(MatchmakingService service, GuestSessionService guestSessionService) {
		return MockMvcBuilders
			.standaloneSetup(new MatchmakingController(service, new MatchParticipantResolver(guestSessionService)))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
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
			super(null, null, null, null, null);
			this.matchStatus = matchStatus;
		}

		@Override
		public MatchStatusResponse join(String participantKey, String gameType) {
			return new MatchStatusResponse(
				participantKey,
				GameName.EYEFIGHT,
				matchStatus,
				matchStatus == MatchStatus.SEARCHING ? null : ROOM_ID,
				QUEUED_AT,
				null,
				null
			);
		}

		@Override
		public MatchStatusResponse cancel(String participantKey) {
			return new MatchStatusResponse(
				participantKey,
				GameName.EYEFIGHT,
				MatchStatus.CANCELLED,
				null,
				QUEUED_AT,
				null,
				null
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

	private static final class StubGuestSessionService extends GuestSessionService {

		private GuestSession existing;
		private GuestSession toIssue;

		private StubGuestSessionService() {
			super(null, null, new GuestSessionProperties(Duration.ofHours(24)));
		}

		void hasExisting(GuestSession session) {
			this.existing = session;
		}

		void willIssue(GuestSession session) {
			this.toIssue = session;
		}

		@Override
		public Optional<GuestSession> findById(UUID guestSessionId) {
			return Optional.ofNullable(existing);
		}

		@Override
		public GuestSession issue() {
			return toIssue;
		}
	}
}
