package org.ssafy.b102.backend.matchmaking.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.jwt.JwtProperties;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.guest.config.GuestSessionProperties;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.entity.MatchmakingEntry;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;
import org.ssafy.b102.testfixture.websocket.RecordingMatchNotifier;
import org.ssafy.b102.testfixture.websocket.StubWebSocketSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * 핸들러의 인증·등록·정리·즉시푸시 로직을 Spring 없이 단위 테스트한다.
 *
 * <p>회원은 실제 {@link JwtTokenProvider}로 발급한 토큰을, 게스트는 손으로 만든
 * {@link GuestSessionService} 스텁으로 검증한다. 세션은 손으로 만든 스텁을 쓴다.
 * 연결 자체(핸드셰이크)는 별도의 {@code @SpringBootTest} 통합 테스트에서 확인한다.
 */
class MatchWebSocketHandlerTest {

	private static final String SECRET_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
	private static final Long MEMBER_USER_ID = 1L;
	private static final String MEMBER_KEY = "USER:1";
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	private StubMatchmakingService service;
	private MatchSessionRegistry registry;
	private RecordingMatchNotifier notifier;
	private JwtTokenProvider jwtTokenProvider;
	private StubGuestSessionService guestSessionService;
	private MatchWebSocketHandler handler;

	@BeforeEach
	void setUp() {
		service = new StubMatchmakingService();
		registry = new InMemoryMatchSessionRegistry();
		notifier = new RecordingMatchNotifier();
		jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET_KEY, 1800, 1209600));
		guestSessionService = new StubGuestSessionService();
		handler = new MatchWebSocketHandler(
			service, registry, notifier, jwtTokenProvider, guestSessionService, JsonMapper.builder().build());
	}

	// --- 회원 (JWT) ---

	@Test
	void memberTokenRegistersUserKey() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, memberFrame(MEMBER_USER_ID));

		assertThat(registry.find(MEMBER_KEY)).hasValue(session);
		assertThat(session.isOpen()).isTrue();
	}

	@Test
	void invalidTokenClosesSession() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, authFrame("\"accessToken\":\"garbage.token.value\""));

		assertThat(session.isOpen()).isFalse();
	}

	// --- 게스트 ---

	@Test
	void validGuestSessionRegistersGuestKey() throws Exception {
		UUID guestId = UUID.randomUUID();
		guestSessionService.register(guestSession(guestId, "용감한수달"));
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, guestFrame(guestId));

		assertThat(registry.find("GUEST:" + guestId)).hasValue(session);
		assertThat(guestSessionService.issueCalls()).isZero();
	}

	@Test
	void invalidGuestSessionClosesSession() throws Exception {
		UUID unknownId = UUID.randomUUID();
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, guestFrame(unknownId));

		assertThat(session.isOpen()).isFalse();
		assertThat(registry.find("GUEST:" + unknownId)).isEmpty();
		assertThat(guestSessionService.issueCalls()).isZero();
	}

	@Test
	void malformedFrameClosesSession() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, new TextMessage("not-json"));

		assertThat(session.isOpen()).isFalse();
	}

	@Test
	void frameWithoutCredentialsClosesSession() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, new TextMessage("{\"type\":\"AUTH\"}"));

		assertThat(session.isOpen()).isFalse();
	}

	// --- 정리 / 즉시푸시 ---

	@Test
	void connectionClosedUnregistersAndCancelsSilently() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		handler.handleTextMessage(session, memberFrame(MEMBER_USER_ID));

		handler.afterConnectionClosed(session, CloseStatus.NORMAL);

		assertThat(registry.find(MEMBER_KEY)).isEmpty();
		assertThat(service.silentlyCancelled()).containsExactly(MEMBER_KEY);
	}

	@Test
	void connectionClosedBeforeAuthDoesNothing() {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		assertThatCode(() -> handler.afterConnectionClosed(session, CloseStatus.NORMAL))
			.doesNotThrowAnyException();
		assertThat(service.silentlyCancelled()).isEmpty();
	}

	@Test
	void pushesMatchSuccessImmediatelyWhenAlreadyEnteringRoom() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		UUID roomId = UUID.randomUUID();
		service.setEntry(new MatchmakingEntry(
			MEMBER_KEY, GameName.EYEFIGHT, MatchStatus.ENTERING_ROOM, roomId, NOW, NOW, null));

		handler.handleTextMessage(session, memberFrame(MEMBER_USER_ID));

		assertThat(notifier.notified()).singleElement().satisfies(notified -> {
			assertThat(notified.participantKey()).isEqualTo(MEMBER_KEY);
			assertThat(notified.roomId()).isEqualTo(roomId);
			assertThat(notified.gameType()).isEqualTo(GameName.EYEFIGHT);
		});
	}

	@Test
	void doesNotPushWhenStillSearching() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		service.setEntry(new MatchmakingEntry(
			MEMBER_KEY, GameName.EYEFIGHT, MatchStatus.SEARCHING, null, NOW, NOW, null));

		handler.handleTextMessage(session, memberFrame(MEMBER_USER_ID));

		assertThat(notifier.notified()).isEmpty();
	}

	/**
	 * 첫 프레임만 인증에 쓴다. 이후 프레임으로 다른 사람을 밀어넣어 세션을 탈취할 수 없다.
	 */
	@Test
	void ignoresFramesAfterAuthentication() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		handler.handleTextMessage(session, memberFrame(MEMBER_USER_ID));

		handler.handleTextMessage(session, memberFrame(999L));

		assertThat(registry.find("USER:999")).isEmpty();
		assertThat(registry.find(MEMBER_KEY)).hasValue(session);
	}

	private TextMessage memberFrame(Long userId) {
		return authFrame("\"accessToken\":\"" + jwtTokenProvider.issueAccessToken(userId) + "\"");
	}

	private static TextMessage guestFrame(UUID guestSessionId) {
		return authFrame("\"guestSessionId\":\"" + guestSessionId + "\"");
	}

	private static TextMessage authFrame(String credentialField) {
		return new TextMessage("{\"type\":\"AUTH\"," + credentialField + "}");
	}

	private static GuestSession guestSession(UUID id, String nickname) {
		return new GuestSession(id, nickname, NOW, NOW.plus(Duration.ofHours(24)));
	}

	private static final class StubMatchmakingService extends MatchmakingService {

		private final List<String> silentlyCancelled = new ArrayList<>();
		private Optional<MatchmakingEntry> entry = Optional.empty();

		private StubMatchmakingService() {
			super(null, null, null, null, null);
		}

		void setEntry(MatchmakingEntry entry) {
			this.entry = Optional.of(entry);
		}

		List<String> silentlyCancelled() {
			return silentlyCancelled;
		}

		@Override
		public Optional<MatchmakingEntry> findEntry(String participantKey) {
			return entry;
		}

		@Override
		public void cancelSilently(String participantKey) {
			silentlyCancelled.add(participantKey);
		}
	}

	private static final class StubGuestSessionService extends GuestSessionService {

		private final Map<UUID, GuestSession> sessions = new HashMap<>();
		private int issueCalls;

		private StubGuestSessionService() {
			super(null, null, new GuestSessionProperties(Duration.ofHours(24)));
		}

		void register(GuestSession session) {
			sessions.put(session.guestSessionId(), session);
		}

		int issueCalls() {
			return issueCalls;
		}

		@Override
		public GuestSession validate(UUID guestSessionId) {
			GuestSession session = sessions.get(guestSessionId);
			if (session == null) {
				throw new BusinessException(GuestSessionErrorCode.INVALID_GUEST_SESSION);
			}

			return session;
		}

		@Override
		public GuestSession issue() {
			issueCalls++;
			return null;
		}
	}
}
