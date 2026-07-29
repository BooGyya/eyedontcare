package org.ssafy.b102.backend.matchmaking.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.entity.MatchmakingEntry;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;
import org.ssafy.b102.testfixture.websocket.RecordingMatchNotifier;
import org.ssafy.b102.testfixture.websocket.StubWebSocketSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * 핸들러의 인증·등록·정리·즉시푸시 결정 로직을 Spring 없이 단위 테스트한다.
 *
 * <p>세션은 손으로 만든 스텁, 서비스는 하위 클래스 스텁, 레지스트리·매퍼는 실제 구현을 쓴다.
 * 연결 자체(핸드셰이크)는 별도의 {@code @SpringBootTest} 통합 테스트에서 한 번 확인한다.
 */
class MatchWebSocketHandlerTest {

	private static final String PARTICIPANT_KEY = "USER:1";

	private StubMatchmakingService service;
	private MatchSessionRegistry registry;
	private RecordingMatchNotifier notifier;
	private MatchWebSocketHandler handler;

	@BeforeEach
	void setUp() {
		service = new StubMatchmakingService();
		registry = new InMemoryMatchSessionRegistry();
		notifier = new RecordingMatchNotifier();
		handler = new MatchWebSocketHandler(service, registry, notifier, JsonMapper.builder().build());
	}

	@Test
	void authFrameRegistersSession() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, authFrame(PARTICIPANT_KEY));

		assertThat(registry.find(PARTICIPANT_KEY)).hasValue(session);
		assertThat(session.isOpen()).isTrue();
	}

	@Test
	void invalidParticipantKeyClosesSessionAndDoesNotRegister() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, authFrame("ADMIN:1"));

		assertThat(session.isOpen()).isFalse();
		assertThat(registry.find("ADMIN:1")).isEmpty();
	}

	@Test
	void malformedFrameClosesSession() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		handler.handleTextMessage(session, new TextMessage("not-json"));

		assertThat(session.isOpen()).isFalse();
	}

	/**
	 * 연결 종료는 조용한 취소로 이어진다. 등록 해제 후 참가자 키로 {@code cancelSilently}를 부른다.
	 */
	@Test
	void connectionClosedUnregistersAndCancelsSilently() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		handler.handleTextMessage(session, authFrame(PARTICIPANT_KEY));

		handler.afterConnectionClosed(session, CloseStatus.NORMAL);

		assertThat(registry.find(PARTICIPANT_KEY)).isEmpty();
		assertThat(service.silentlyCancelled()).containsExactly(PARTICIPANT_KEY);
	}

	/**
	 * 인증 전에 끊긴 연결은 정리할 것이 없다. 조용한 취소도 호출하지 않는다.
	 */
	@Test
	void connectionClosedBeforeAuthDoesNothing() {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		assertThatCode(() -> handler.afterConnectionClosed(session, CloseStatus.NORMAL))
			.doesNotThrowAnyException();
		assertThat(service.silentlyCancelled()).isEmpty();
	}

	/**
	 * 연결 순서 레이스 보강. 신청보다 늦게 연결해 이미 성사돼 있으면 연결 즉시 MATCH_SUCCESS를 보낸다.
	 */
	@Test
	void pushesMatchSuccessImmediatelyWhenAlreadyEnteringRoom() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		UUID roomId = UUID.randomUUID();
		service.setEntry(new MatchmakingEntry(
			PARTICIPANT_KEY, GameName.EYEFIGHT, MatchStatus.ENTERING_ROOM, roomId, Instant.now(), Instant.now()));

		handler.handleTextMessage(session, authFrame(PARTICIPANT_KEY));

		assertThat(notifier.notified()).singleElement().satisfies(notified -> {
			assertThat(notified.participantKey()).isEqualTo(PARTICIPANT_KEY);
			assertThat(notified.roomId()).isEqualTo(roomId);
			assertThat(notified.gameType()).isEqualTo(GameName.EYEFIGHT);
		});
	}

	@Test
	void doesNotPushWhenStillSearching() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		service.setEntry(new MatchmakingEntry(
			PARTICIPANT_KEY, GameName.EYEFIGHT, MatchStatus.SEARCHING, null, Instant.now(), Instant.now()));

		handler.handleTextMessage(session, authFrame(PARTICIPANT_KEY));

		assertThat(notifier.notified()).isEmpty();
	}

	/**
	 * 첫 프레임만 인증에 쓴다. 이후 프레임으로 다른 참가자 키를 밀어넣어 세션을 탈취할 수 없다.
	 */
	@Test
	void ignoresFramesAfterAuthentication() throws Exception {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		handler.handleTextMessage(session, authFrame(PARTICIPANT_KEY));

		handler.handleTextMessage(session, authFrame("USER:999"));

		assertThat(registry.find("USER:999")).isEmpty();
		assertThat(registry.find(PARTICIPANT_KEY)).hasValue(session);
	}

	private static TextMessage authFrame(String participantKey) {
		return new TextMessage("{\"type\":\"AUTH\",\"participantKey\":\"" + participantKey + "\"}");
	}

	private static final class StubMatchmakingService extends MatchmakingService {

		private final List<String> silentlyCancelled = new ArrayList<>();
		private Optional<MatchmakingEntry> entry = Optional.empty();

		private StubMatchmakingService() {
			super(null, null, null);
		}

		void setEntry(MatchmakingEntry entry) {
			this.entry = Optional.of(entry);
		}

		List<String> silentlyCancelled() {
			return silentlyCancelled;
		}

		@Override
		public void validateParticipant(String participantKey) {
			if (!participantKey.startsWith("USER:") && !participantKey.startsWith("GUEST:")) {
				throw new BusinessException(MatchmakingErrorCode.INVALID_PARTICIPANT_KEY);
			}
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
}
