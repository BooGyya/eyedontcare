package org.ssafy.b102.backend.matchmaking.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.service.MatchNotifier;
import org.ssafy.b102.testfixture.websocket.StubWebSocketSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * 성사 알림 전송 로직을 Spring 없이 단위 테스트한다.
 *
 * <p>세션 조회는 실제 in-memory 레지스트리를, 직렬화는 실제 Jackson 3 매퍼를 쓰되
 * 세션은 손으로 만든 스텁으로 대신한다.
 */
class WebSocketMatchNotifierTest {

	private static final String PARTICIPANT_KEY = "USER:1";

	private MatchSessionRegistry registry;
	private JsonMapper jsonMapper;
	private MatchNotifier notifier;

	@BeforeEach
	void setUp() {
		registry = new InMemoryMatchSessionRegistry();
		jsonMapper = JsonMapper.builder().build();
		notifier = new WebSocketMatchNotifier(registry, jsonMapper);
	}

	@Test
	void sendsMatchSuccessFrameToRegisteredSession() {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		registry.register(PARTICIPANT_KEY, session);
		UUID roomId = UUID.randomUUID();

		notifier.notifyMatched(PARTICIPANT_KEY, roomId, GameName.EYEFIGHT);

		Map<String, Object> frame = jsonMapper.readValue(session.lastSentPayload(), Map.class);
		assertThat(frame).containsOnlyKeys("type", "roomId", "gameType");
		assertThat(frame.get("type")).isEqualTo("MATCH_SUCCESS");
		assertThat(frame.get("roomId")).isEqualTo(roomId.toString());
		assertThat(frame.get("gameType")).isEqualTo("EYEFIGHT");
	}

	@Test
	void doesNothingWhenParticipantHasNoSession() {
		assertThatCode(() -> notifier.notifyMatched(PARTICIPANT_KEY, UUID.randomUUID(), GameName.HOCKEY))
			.doesNotThrowAnyException();
	}

	@Test
	void sendsMatchRequeuedFrame() {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		registry.register(PARTICIPANT_KEY, session);

		notifier.notifyRequeued(PARTICIPANT_KEY, GameName.HOCKEY);

		Map<String, Object> frame = jsonMapper.readValue(session.lastSentPayload(), Map.class);
		assertThat(frame).containsOnlyKeys("type", "gameType");
		assertThat(frame.get("type")).isEqualTo("MATCH_REQUEUED");
		assertThat(frame.get("gameType")).isEqualTo("HOCKEY");
	}

	@Test
	void sendsMatchErrorFrame() {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		registry.register(PARTICIPANT_KEY, session);

		notifier.notifyError(PARTICIPANT_KEY, MatchmakingErrorCode.REMATCH_FAILED);

		Map<String, Object> frame = jsonMapper.readValue(session.lastSentPayload(), Map.class);
		assertThat(frame).containsOnlyKeys("type", "code", "message");
		assertThat(frame.get("type")).isEqualTo("MATCH_ERROR");
		assertThat(frame.get("code")).isEqualTo("MATCHMAKING-007");
		assertThat(frame.get("message")).isEqualTo("자동 재매칭 요청을 처리하지 못했습니다.");
	}

	/**
	 * 이미 닫힌 세션에는 보내지 않는다. 닫힌 세션에 전송하면 스텁이 예외를 던진다.
	 */
	@Test
	void skipsClosedSession() {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		registry.register(PARTICIPANT_KEY, session);
		session.close();

		notifier.notifyMatched(PARTICIPANT_KEY, UUID.randomUUID(), GameName.HOCKEY);

		assertThat(session.sentPayloads()).isEmpty();
	}
}
