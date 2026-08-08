package org.ssafy.b102.backend.guest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.guest.config.GuestSessionIssueRateLimitProperties;
import org.ssafy.b102.backend.guest.config.GuestSessionProperties;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestSessionIssueRateLimiter;

class GuestSessionControllerTest {

	private static final String PATH = "/api/v1/guests/session";
	private static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";
	private static final UUID EXISTING_ID =
		UUID.fromString("27868019-1a91-40d3-8536-a0e5dcf7e8cf");
	private static final UUID ISSUED_ID =
		UUID.fromString("20fa9eef-9d8f-47f7-b159-c9768a82d57d");
	private static final Instant CREATED_AT = Instant.parse("2026-08-08T00:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-08-09T00:00:00Z");

	@Test
	void issuesNewSessionWhenClientHasNoIdentity() throws Exception {
		StubGuestSessionService service = new StubGuestSessionService();

		mockMvc(service, new StubRateLimiter())
			.perform(post(PATH))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("GUEST_SESSION_READY"))
			.andExpect(jsonPath("$.data.guestSessionId").value(ISSUED_ID.toString()))
			.andExpect(jsonPath("$.data.nickname").value("발급된수달"))
			.andExpect(jsonPath("$.data.expiresAt").value("2026-08-09T00:00:00Z"));

		assertThat(service.issueCount).isEqualTo(1);
	}

	@Test
	void reusesStillValidSessionWithoutIssuingAnother() throws Exception {
		StubGuestSessionService service = new StubGuestSessionService();
		service.hasExisting(guestSession(EXISTING_ID, "기존수달"));

		mockMvc(service, new StubRateLimiter())
			.perform(post(PATH).header(GUEST_SESSION_HEADER, EXISTING_ID.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.guestSessionId").value(EXISTING_ID.toString()))
			.andExpect(jsonPath("$.data.nickname").value("기존수달"));

		assertThat(service.issueCount).isZero();
	}

	@Test
	void issuesNewSessionWhenStoredIdAlreadyExpired() throws Exception {
		StubGuestSessionService service = new StubGuestSessionService();

		mockMvc(service, new StubRateLimiter())
			.perform(post(PATH).header(GUEST_SESSION_HEADER, EXISTING_ID.toString()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.guestSessionId").value(ISSUED_ID.toString()));

		assertThat(service.issueCount).isEqualTo(1);
	}

	/**
	 * 신원을 확보하는 첫 관문이므로, 저장소에 남은 깨진 값 하나로 막히면 안 된다. 400이 아니라
	 * 새 세션을 내줘야 클라이언트가 스스로 회복한다.
	 */
	@Test
	void issuesNewSessionWhenStoredIdIsMalformed() throws Exception {
		StubGuestSessionService service = new StubGuestSessionService();

		mockMvc(service, new StubRateLimiter())
			.perform(post(PATH).header(GUEST_SESSION_HEADER, "not-a-uuid"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.guestSessionId").value(ISSUED_ID.toString()));

		assertThat(service.issueCount).isEqualTo(1);
	}

	@Test
	void rejectsIssueWhenRateLimited() throws Exception {
		StubGuestSessionService service = new StubGuestSessionService();

		mockMvc(service, StubRateLimiter.rejecting())
			.perform(post(PATH))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("GUEST-003"));

		assertThat(service.issueCount).isZero();
	}

	/** 세션을 이미 가진 사용자가 새로고침을 반복해도 발급 상한을 소모하면 안 된다. */
	@Test
	void doesNotConsumeRateLimitWhenReusingSession() throws Exception {
		StubGuestSessionService service = new StubGuestSessionService();
		service.hasExisting(guestSession(EXISTING_ID, "기존수달"));
		StubRateLimiter rateLimiter = new StubRateLimiter();

		mockMvc(service, rateLimiter)
			.perform(post(PATH).header(GUEST_SESSION_HEADER, EXISTING_ID.toString()))
			.andExpect(status().isOk());

		assertThat(rateLimiter.checkCount).isZero();
	}

	@Test
	void limitsByClientIpFromForwardedHeader() throws Exception {
		StubRateLimiter rateLimiter = new StubRateLimiter();

		mockMvc(new StubGuestSessionService(), rateLimiter)
			.perform(post(PATH).header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
			.andExpect(status().isCreated());

		assertThat(rateLimiter.lastClientId).isEqualTo("203.0.113.7");
	}

	private MockMvc mockMvc(
		GuestSessionService service,
		GuestSessionIssueRateLimiter rateLimiter
	) {
		return MockMvcBuilders
			.standaloneSetup(new GuestSessionController(service, rateLimiter))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	private static GuestSession guestSession(UUID id, String nickname) {
		return new GuestSession(id, nickname, CREATED_AT, EXPIRES_AT);
	}

	private static class StubGuestSessionService extends GuestSessionService {

		private GuestSession existing;
		private int issueCount;

		private StubGuestSessionService() {
			super(null, null, new GuestSessionProperties(Duration.ofHours(24)));
		}

		void hasExisting(GuestSession session) {
			this.existing = session;
		}

		@Override
		public Optional<GuestSession> findById(UUID guestSessionId) {
			return Optional.ofNullable(existing)
				.filter(session -> session.guestSessionId().equals(guestSessionId));
		}

		@Override
		public GuestSession issue() {
			issueCount++;

			return guestSession(ISSUED_ID, "발급된수달");
		}
	}

	private static class StubRateLimiter extends GuestSessionIssueRateLimiter {

		private final boolean rejects;
		private int checkCount;
		private String lastClientId;

		private StubRateLimiter() {
			this(false);
		}

		private StubRateLimiter(boolean rejects) {
			super(null, null, new GuestSessionIssueRateLimitProperties(30, Duration.ofMinutes(1)));
			this.rejects = rejects;
		}

		static StubRateLimiter rejecting() {
			return new StubRateLimiter(true);
		}

		@Override
		public void check(String clientId) {
			checkCount++;
			lastClientId = clientId;
			if (rejects) {
				throw new BusinessException(GuestSessionErrorCode.GUEST_SESSION_ISSUE_RATE_LIMITED);
			}
		}
	}
}
