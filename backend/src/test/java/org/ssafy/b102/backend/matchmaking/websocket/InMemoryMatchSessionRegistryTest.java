package org.ssafy.b102.backend.matchmaking.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.testfixture.websocket.StubWebSocketSession;

/**
 * 참가자 키와 세션의 매핑만 검증한다. Spring 없이 단위 테스트한다.
 *
 * <p>다중 인스턴스에서는 이 in-memory 구현이 깨진다(두 참가자가 다른 인스턴스에 붙을 수 있음).
 * 그때는 Redis Pub/Sub 구현으로 교체하며, 인터페이스가 그 교체 지점이다.
 */
class InMemoryMatchSessionRegistryTest {

	private static final String PARTICIPANT_KEY = "USER:1";

	private MatchSessionRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new InMemoryMatchSessionRegistry();
	}

	@Test
	void findReturnsRegisteredSession() {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		registry.register(PARTICIPANT_KEY, session);

		assertThat(registry.find(PARTICIPANT_KEY)).hasValue(session);
	}

	@Test
	void findReturnsEmptyForUnknownKey() {
		assertThat(registry.find(PARTICIPANT_KEY)).isEmpty();
	}

	@Test
	void unregisterReturnsKeyAndRemovesSession() {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		registry.register(PARTICIPANT_KEY, session);

		assertThat(registry.unregister(session)).hasValue(PARTICIPANT_KEY);
		assertThat(registry.find(PARTICIPANT_KEY)).isEmpty();
	}

	@Test
	void unregisterReturnsEmptyForUnknownSession() {
		StubWebSocketSession session = new StubWebSocketSession("s1");

		assertThat(registry.unregister(session)).isEmpty();
	}

	/**
	 * 같은 키로 재연결하면 최신 세션이 이긴다. 뒤늦게 닫힌 옛 세션이 새 세션을 지워서는 안 된다.
	 */
	@Test
	void reconnectKeepsLatestSessionWhenOldSessionCloses() {
		StubWebSocketSession oldSession = new StubWebSocketSession("old");
		StubWebSocketSession newSession = new StubWebSocketSession("new");
		registry.register(PARTICIPANT_KEY, oldSession);
		registry.register(PARTICIPANT_KEY, newSession);

		registry.unregister(oldSession);

		assertThat(registry.find(PARTICIPANT_KEY)).hasValue(newSession);
	}

	/**
	 * 옛 세션 종료는 키를 돌려주지 않는다. 그래야 옛 세션의 close가 새 세션의 매칭을 취소하지 않는다.
	 */
	@Test
	void oldSessionUnregisterReturnsEmptyAfterReconnect() {
		StubWebSocketSession oldSession = new StubWebSocketSession("old");
		StubWebSocketSession newSession = new StubWebSocketSession("new");
		registry.register(PARTICIPANT_KEY, oldSession);
		registry.register(PARTICIPANT_KEY, newSession);

		assertThat(registry.unregister(oldSession)).isEmpty();
		assertThat(registry.unregister(newSession)).hasValue(PARTICIPANT_KEY);
	}
}
